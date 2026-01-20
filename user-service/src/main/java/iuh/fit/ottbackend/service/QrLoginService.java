package iuh.fit.ottbackend.service;

import iuh.fit.ottbackend.dto.request.QrConfirmRequest;
import iuh.fit.ottbackend.dto.request.QrGenerateRequest;
import iuh.fit.ottbackend.dto.request.QrScanRequest;
import iuh.fit.ottbackend.dto.response.QrCodeResponse;
import iuh.fit.ottbackend.dto.response.QrStatusResponse;
import iuh.fit.ottbackend.entity.QrCode;
import iuh.fit.ottbackend.entity.QrLoginSession;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.UserSession;
import iuh.fit.ottbackend.entity.enums.LoginMethod;
import iuh.fit.ottbackend.entity.enums.QrCodeStatus;
import iuh.fit.ottbackend.entity.enums.QrCodeType;
import iuh.fit.ottbackend.entity.enums.QrLoginSessionStatus;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.mapper.QrCodeMapper;
import iuh.fit.ottbackend.repository.QrCodeRepository;
import iuh.fit.ottbackend.repository.QrLoginSessionRepository;
import iuh.fit.ottbackend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrLoginService {

    private static final int QR_EXPIRY_MINUTES = 3;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int MAX_PENDING_QR_LOGINS = 5;

    private final QrCodeRepository qrCodeRepository;
    private final QrLoginSessionRepository qrLoginSessionRepository;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final QrCodeMapper qrCodeMapper;

    @Transactional
    public QrCodeResponse generateLoginQrCode(QrGenerateRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
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

        return qrCodeMapper.toQrCodeResponse(qrCode);
    }

    @Transactional
    public QrStatusResponse scanQrCode(QrScanRequest request, String userId) {
        QrCode qrCode = verifyQrCode(request.getQrData());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        validateUserStatus(user);

        long pendingCount = qrCodeRepository.countByUserAndStatus(user, QrCodeStatus.SCANNED);
        if (pendingCount >= MAX_PENDING_QR_LOGINS) {
            throw new AppException(ErrorCode.TOO_MANY_PENDING_QR_LOGINS);
        }

        qrCode.setUser(user);
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
                .user(user)
                .status(QrLoginSessionStatus.WAITING)
                .build();
        qrLoginSessionRepository.save(loginSession);

        QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);
        response.setMessage("QR code scanned successfully. Please confirm to login.");
        return response;
    }

    @Transactional
    public QrStatusResponse confirmQrLogin(QrConfirmRequest request, String userId) {
        QrCode qrCode = qrCodeRepository.findById(request.getQrId())
                .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

        if (qrCode.getStatus() != QrCodeStatus.SCANNED) {
            throw new AppException(ErrorCode.INVALID_QR_STATUS);
        }

        if (!userId.equals(qrCode.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            qrCode.setStatus(QrCodeStatus.EXPIRED);
            qrCodeRepository.save(qrCode);
            throw new AppException(ErrorCode.QR_CODE_EXPIRED);
        }

        QrLoginSession loginSession = qrLoginSessionRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        if (!request.isConfirmed()) {
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

        User user = qrCode.getUser();
        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        UserSession session = sessionService.createUserSession(
                user,
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

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);
        response.setSessionToken(token);
        response.setRefreshToken(refreshToken);
        response.setExpiresAt(session.getExpiresAt());
        response.setMessage("Login successful");
        return response;
    }

    public QrStatusResponse checkQrStatus(String qrId) {
        QrCode qrCode = qrCodeRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

        if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (qrCode.getStatus() != QrCodeStatus.EXPIRED &&
                    qrCode.getStatus() != QrCodeStatus.CONFIRMED) {
                qrCode.setStatus(QrCodeStatus.EXPIRED);
                qrCode = qrCodeRepository.save(qrCode);
            }
        }

        QrStatusResponse response = qrCodeMapper.toQrStatusResponse(qrCode);

        if (qrCode.getStatus() == QrCodeStatus.CONFIRMED) {
            UserSession session = sessionService.findActiveSessionByDeviceAndUser(
                    qrCode.getDeviceId(), qrCode.getUser());

            response.setSessionToken(session.getSessionToken());
            response.setRefreshToken(session.getRefreshToken());
            response.setExpiresAt(session.getExpiresAt());
            response.setMessage("Login successful");

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
        QrCode qrCode = qrCodeRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_CODE_NOT_FOUND));

        if (qrCode.getStatus() == QrCodeStatus.PENDING ||
                qrCode.getStatus() == QrCodeStatus.SCANNED) {
            qrCode.setStatus(QrCodeStatus.CANCELLED);
            qrCodeRepository.save(qrCode);
        }
    }

    private QrCode verifyQrCode(String qrData) {
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
                throw new AppException(ErrorCode.INVALID_QR_CODE);
            }

            if (qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                qrCode.setStatus(QrCodeStatus.EXPIRED);
                qrCodeRepository.save(qrCode);
                throw new AppException(ErrorCode.QR_CODE_EXPIRED);
            }

            if (qrCode.getStatus() != QrCodeStatus.PENDING) {
                throw new AppException(ErrorCode.QR_CODE_ALREADY_USED);
            }

            return qrCode;

        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_QR_CODE);
        }
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateUserStatus(User user) {
        if (user.getDeletedAt() != null) {
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        if (!user.getIsActive()) {
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }
    }
}