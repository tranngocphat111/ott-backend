package iuh.fit.authservice.service;

import iuh.fit.authservice.dto.request.QrConfirmRequest;
import iuh.fit.authservice.dto.request.QrGenerateRequest;
import iuh.fit.authservice.dto.request.QrScanRequest;
import iuh.fit.authservice.dto.response.QrCodeResponse;
import iuh.fit.authservice.dto.response.QrStatusResponse;
import iuh.fit.authservice.entity.*;
import iuh.fit.authservice.entity.enums.*;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import iuh.fit.authservice.mapper.QrCodeMapper;
import iuh.fit.authservice.repository.QrCodeRepository;
import iuh.fit.authservice.repository.QrLoginSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrLoginService {

    private static final int QR_EXPIRY_MINUTES = 3;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int MAX_PENDING_QR_LOGINS = 5;

    private final QrCodeRepository qrCodeRepository;
    private final QrLoginSessionRepository qrLoginSessionRepository;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final QrCodeMapper qrCodeMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public QrCodeResponse generateLoginQrCode(QrGenerateRequest request) {
        log.info("Generating QR login code for deviceId: {}", request.getDeviceId());

        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            log.warn("Invalid deviceId provided");
            throw new AppException(ErrorCode.INVALID_DEVICE_ID);
        }

        String secretToken = generateSecureToken();
        String qrId = UUID.randomUUID().toString();
        String qrData = qrId + ":" + secretToken;

        QrCode qrCode = QrCode.builder()
                .id(qrId)
                .qrType(QrCodeType.LOGIN)
                .qrData(Base64.getEncoder().encodeToString(qrData.getBytes()))
                .deviceId(request.getDeviceId())
                .deviceType(request.getDeviceType())
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .status(QrCodeStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(QR_EXPIRY_MINUTES))
                .failedAttempts(0)
                .build();

        qrCode = qrCodeRepository.save(qrCode);
        log.info("QR login code generated successfully - qrId: {}", qrId);

        return qrCodeMapper.toQrCodeResponse(qrCode);
    }

    @Transactional
    public QrStatusResponse scanQrCode(QrScanRequest request, String userId) {
        log.info("Scanning QR code for userId: {}", userId);

        QrCode qrCode = verifyQrCode(request.getQrData());

        UserServiceClient.UserDto userDto = userServiceClient.getUserById(userId);
        validateUserStatus(userDto);

        long pendingCount = qrCodeRepository.countByUserIdAndStatus(userId, QrCodeStatus.SCANNED);
        if (pendingCount >= MAX_PENDING_QR_LOGINS) {
            log.warn("User {} has too many pending QR logins: {}", userId, pendingCount);
            throw new AppException(ErrorCode.TOO_MANY_PENDING_QR_LOGINS);
        }

        qrCode.setUserId(userId);
        qrCode.setStatus(QrCodeStatus.SCANNED);
        qrCode.setScannedAt(LocalDateTime.now());
        qrCode.setScannedDeviceId(request.getDeviceId());
        qrCode.setScannedDeviceType(request.getDeviceType());
        qrCode.setScannedDeviceInfo(request.getDeviceInfo());
        qrCode.setScannedIpAddress(request.getIpAddress());
        qrCode.setLocation(request.getLocation());
        qrCode = qrCodeRepository.save(qrCode);

        QrLoginSession loginSession = QrLoginSession.builder()
                .qrCode(qrCode)
                .userId(userId)
                .status(QrLoginSessionStatus.WAITING)
                .build();
        qrLoginSessionRepository.save(loginSession);

        log.info("QR code scanned successfully - qrId: {}, userId: {}", qrCode.getId(), userId);

        QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);
        response.setMessage("QR code scanned successfully. Please confirm to login.");
        return response;
    }

    @Transactional
    public QrStatusResponse confirmQrLogin(QrConfirmRequest request, String userId) {
        log.info("Confirming QR login - qrId: {}, userId: {}, confirmed: {}",
                request.getQrId(), userId, request.getConfirmed());

        QrCode qrCode = qrCodeRepository.findById(request.getQrId())
                .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

        if (qrCode.getStatus() != QrCodeStatus.SCANNED) {
            log.warn("Invalid QR status for confirmation: {}", qrCode.getStatus());
            throw new AppException(ErrorCode.INVALID_QR_STATUS);
        }

        if (!userId.equals(qrCode.getUserId())) {
            log.warn("Unauthorized QR confirmation attempt");
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            qrCode.setStatus(QrCodeStatus.EXPIRED);
            qrCodeRepository.save(qrCode);
            throw new AppException(ErrorCode.QR_CODE_EXPIRED);
        }

        QrLoginSession loginSession = qrLoginSessionRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        if (!request.getConfirmed()) {
            log.info("User rejected QR login - qrId: {}", qrCode.getId());
            qrCode.setStatus(QrCodeStatus.CANCELLED);
            qrCode = qrCodeRepository.save(qrCode);

            loginSession.setStatus(QrLoginSessionStatus.REJECTED);
            loginSession.setRejectedAt(LocalDateTime.now());
            loginSession.setRejectionReason("User rejected login");
            qrLoginSessionRepository.save(loginSession);

            QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);
            response.setMessage("Login request cancelled");
            return response;
        }

        UserServiceClient.UserDto userDto = userServiceClient.getUserById(userId);
        String token = jwtService.generateToken(userDto);
        String refreshToken = jwtService.generateRefreshToken();

        UserSession session = sessionService.createUserSession(
                userId,
                qrCode.getDeviceId(),
                qrCode.getDeviceType(),
                null,
                qrCode.getIpAddress(),
                qrCode.getDeviceInfo(),
                token,
                refreshToken,
                LoginMethod.QR_CODE
        );

        qrCode.setStatus(QrCodeStatus.CONFIRMED);
        qrCode.setConfirmedAt(LocalDateTime.now());
        qrCode = qrCodeRepository.save(qrCode);

        loginSession.setStatus(QrLoginSessionStatus.AUTHORIZED);
        loginSession.setSession(session);
        loginSession.setAuthorizedAt(LocalDateTime.now());
        qrLoginSessionRepository.save(loginSession);

        userServiceClient.updateLastLogin(userId);

        log.info("QR login confirmed successfully - qrId: {}, userId: {}", qrCode.getId(), userId);

        QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);
        response.setSessionToken(token);
        response.setRefreshToken(refreshToken);
        response.setExpiresAt(session.getExpiresAt());
        response.setMessage("Login successful");
        return response;
    }

    @Transactional
    public QrStatusResponse checkQrStatus(String qrId) {
        log.debug("Checking QR status for qrId: {}", qrId);

        QrCode qrCode = qrCodeRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

        // Auto-expire nếu quá hạn
        if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (qrCode.getStatus() != QrCodeStatus.EXPIRED &&
                    qrCode.getStatus() != QrCodeStatus.CONFIRMED) {
                qrCode.setStatus(QrCodeStatus.EXPIRED);
                qrCode = qrCodeRepository.save(qrCode);
                log.info("QR code auto expired - qrId: {}", qrId);
            }
        }

        QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);

        if (qrCode.getStatus() == QrCodeStatus.CONFIRMED) {
            QrLoginSession loginSession = qrLoginSessionRepository.findByQrCode(qrCode)
                    .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

            UserSession session = loginSession.getSession();
            if (session == null) {
                throw new AppException(ErrorCode.SESSION_NOT_FOUND);
            }

            response.setSessionToken(session.getSessionToken());
            response.setRefreshToken(session.getRefreshToken());
            response.setExpiresAt(session.getExpiresAt());
            response.setMessage("Login successful");
            log.info("QR login already confirmed - qrId: {}", qrId);

        } else if (qrCode.getStatus() == QrCodeStatus.SCANNED) {
            response.setMessage("QR code scanned. Waiting for confirmation...");

        } else if (qrCode.getStatus() == QrCodeStatus.EXPIRED) {
            response.setMessage("QR code expired. Please generate a new one.");

        } else if (qrCode.getStatus() == QrCodeStatus.CANCELLED) {
            response.setMessage("Login request was cancelled.");

        } else {
            response.setMessage("Waiting for QR code to be scanned...");
        }

        return response;
    }

    @Transactional
    public void cancelQrCode(String qrId) {
        log.info("Cancelling QR code - qrId: {}", qrId);

        QrCode qrCode = qrCodeRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

        if (qrCode.getStatus() == QrCodeStatus.PENDING ||
                qrCode.getStatus() == QrCodeStatus.SCANNED) {
            qrCode.setStatus(QrCodeStatus.CANCELLED);
            qrCodeRepository.save(qrCode);
            log.info("QR code cancelled successfully - qrId: {}", qrId);
        }
    }

    private QrCode verifyQrCode(String qrData) {
        log.debug("Verifying QR code data");

        try {
            String decodedData = new String(Base64.getDecoder().decode(qrData));
            String[] parts = decodedData.split(":");

            if (parts.length != 2) {
                throw new AppException(ErrorCode.INVALID_QR_CODE);
            }

            String qrId = parts[0];

            QrCode qrCode = qrCodeRepository.findById(qrId)
                    .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

            String storedData = new String(Base64.getDecoder().decode(qrCode.getQrData()));
            if (!storedData.equals(decodedData)) {
                qrCode.setFailedAttempts(qrCode.getFailedAttempts() + 1);

                if (qrCode.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                    qrCode.setStatus(QrCodeStatus.EXPIRED);
                }

                qrCodeRepository.save(qrCode);
                log.warn("Invalid QR data attempt - qrId: {}", qrId);
                throw new AppException(ErrorCode.INVALID_QR_CODE);
            }

            if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                qrCode.setStatus(QrCodeStatus.EXPIRED);
                qrCodeRepository.save(qrCode);
                throw new AppException(ErrorCode.QR_CODE_EXPIRED);
            }

            if (qrCode.getStatus() != QrCodeStatus.PENDING) {
                log.warn("QR code already used - qrId: {}", qrId);
                throw new AppException(ErrorCode.QR_CODE_ALREADY_USED);
            }

            return qrCode;

        } catch (IllegalArgumentException e) {
            log.warn("Invalid Base64 QR data");
            throw new AppException(ErrorCode.INVALID_QR_CODE);
        }
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateUserStatus(UserServiceClient.UserDto user) {
        if (user.getDeletedAt() != null) {
            log.warn("Deleted account attempted QR login - userId: {}", user.getId());
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Inactive account attempted QR login - userId: {}", user.getId());
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("Blocked account attempted QR login - userId: {}", user.getId());
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
        }
    }
}