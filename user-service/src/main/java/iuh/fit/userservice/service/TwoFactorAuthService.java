package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.request.*;
import iuh.fit.userservice.dto.response.*;
import iuh.fit.userservice.entity.OtpCode;
import iuh.fit.userservice.entity.TwoFactorAuth;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.OtpType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.repository.TwoFactorAuthRepository;
import iuh.fit.userservice.utils.UserValidationUtil;
import iuh.fit.userservice.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorAuthService {

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final OtpService otpService;
    private final NotificationPublisher notificationPublisher;
    private final UserValidationUtil userValidationUtil;
    private final ValidationUtils validationUtils;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public OtpResponse request2FAEnable(String userId, Request2FAEnableOtpRequest request) {
        log.info("2FA enable request received for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);

        if (twoFactorAuthRepository.existsByUserIdAndIsEnabledTrue(userId)) {
            log.warn("2FA is already enabled for userId: {}", userId);
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_ALREADY_ENABLED);
        }

        OtpCode otpCode = otpService.generateOtp(user, null, user.getEmail(), OtpType.ENABLE_TWO_FACTOR, request.getIpAddress());
        notificationPublisher.sendOtpEmail(user.getEmail(), user.getFullName(), otpCode.getCode(), OtpType.ENABLE_TWO_FACTOR, request.getIpAddress(), null, userId);

        log.info("2FA enable OTP sent successfully to userId: {}", userId);

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP sent to your email to enable 2FA")
                .build();
    }

    @Transactional
    public Enable2FAResponse enable2FA(String userId, Enable2FARequest request) {
        log.info("Enabling 2FA for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);

        if (twoFactorAuthRepository.existsByUserIdAndIsEnabledTrue(userId)) {
            log.warn("2FA is already enabled for userId: {}", userId);
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_ALREADY_ENABLED);
        }

        OtpCode otpCode = otpService.validateOtp(null, user.getEmail(), request.getOtp(), OtpType.ENABLE_TWO_FACTOR);


        String[] rawCodes = generateBackupCodes(10);

        String[] hashedCodes = Arrays.stream(rawCodes)
                .map(passwordEncoder::encode)
                .toArray(String[]::new);

        TwoFactorAuth twoFA = twoFactorAuthRepository.findByUserId(userId)
                .orElse(TwoFactorAuth.builder()
                        .user(user)
                        .build());

        twoFA.setBackupCodes(hashedCodes);
        twoFA.setTotalBackupCodes(10);
        twoFA.setBackupCodesUsed(0);
        twoFA.enable();
        twoFactorAuthRepository.save(twoFA);

        otpService.markOtpAsUsed(otpCode);

        log.info("2FA enabled successfully for userId: {}", userId);

        return Enable2FAResponse.builder()
                .enabled(true)
                .backupCodes(rawCodes)
                .message("2FA enabled successfully. Save your backup codes!")
                .build();
    }

    @Transactional
    public OtpResponse request2FADisable(String userId, Request2FADisableOtpRequest request) {
        log.info("2FA disable request received for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);

        if (!twoFactorAuthRepository.existsByUserIdAndIsEnabledTrue(userId)) {
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED);
        }

        if (user.getPasswordHash() == null) {
            throw new AppException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password for 2FA disable request, userId: {}", userId);
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }

        OtpCode otpCode = otpService.generateOtp(user, null, user.getEmail(), OtpType.DISABLE_TWO_FACTOR, request.getIpAddress());
        notificationPublisher.sendOtpEmail(user.getEmail(), user.getFullName(), otpCode.getCode(),
                OtpType.DISABLE_TWO_FACTOR, request.getIpAddress(), null, userId);

        log.info("2FA disable OTP sent successfully to userId: {}", userId);

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP sent to your email to disable 2FA")
                .build();
    }

    @Transactional
    public void disable2FA(String userId, Disable2FARequest request) {
        log.info("Disabling 2FA for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);

        TwoFactorAuth twoFA = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED));

        if (!Boolean.TRUE.equals(twoFA.getIsEnabled())) {
            log.warn("2FA is not enabled for userId: {}", userId);
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED);
        }

        OtpCode otpCode = otpService.validateOtp(null, user.getEmail(), request.getOtp(), OtpType.DISABLE_TWO_FACTOR);

        twoFA.disable();
        twoFactorAuthRepository.save(twoFA);
        otpService.markOtpAsUsed(otpCode);

        log.info("2FA disabled successfully for userId: {}", userId);
    }

    public TwoFactorAuthStatus get2FAStatus(String userId) {
        log.debug("Fetching 2FA status for userId: {}", userId);

        return twoFactorAuthRepository.findByUserId(userId)
                .map(twoFA -> {
                    log.debug("2FA is enabled for userId: {}", userId);
                    return TwoFactorAuthStatus.builder()
                            .enabled(Boolean.TRUE.equals(twoFA.getIsEnabled()))
                            .enabledAt(twoFA.getEnabledAt())
                            .lastUsedAt(twoFA.getLastUsedAt())
                            .remainingBackupCodes(twoFA.getRemainingBackupCodes())
                            .build();
                })
                .orElseGet(() -> {
                    log.debug("2FA is not enabled for userId: {}", userId);
                    return TwoFactorAuthStatus.builder().enabled(false).remainingBackupCodes(0).build();
                });
    }

    public boolean is2FAEnabled(String userId) {
        boolean enabled = twoFactorAuthRepository.existsByUserIdAndIsEnabledTrue(userId);
        log.debug("2FA enabled check for userId: {} -> {}", userId, enabled);
        return enabled;
    }

    private String[] generateBackupCodes(int count) {
        log.debug("Generating {} backup codes", count);
        String[] codes = new String[count];
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < count; i++) {
            codes[i] = String.format("%08d", random.nextInt(100_000_000));
        }
        return codes;
    }
}