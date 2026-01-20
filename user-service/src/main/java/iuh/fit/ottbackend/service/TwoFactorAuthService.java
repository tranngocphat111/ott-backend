package iuh.fit.ottbackend.service;

import iuh.fit.ottbackend.dto.request.*;
import iuh.fit.ottbackend.dto.response.*;
import iuh.fit.ottbackend.entity.OtpCode;
import iuh.fit.ottbackend.entity.TwoFactorAuth;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.enums.OtpType;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.repository.TwoFactorAuthRepository;
import iuh.fit.ottbackend.utils.UserValidationUtil;
import iuh.fit.ottbackend.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ValidationUtils validationUtils;
    private final UserValidationUtil userValidationUtil;

    private static final int BACKUP_CODES_COUNT = 10;

    @Transactional
    public OtpResponse request2FAEnable(String userId, Request2FAEnableOtpRequest request) {
        User user = userValidationUtil.getUserById(userId);

        TwoFactorAuth existing2FA = twoFactorAuthRepository.findByUserId(userId).orElse(null);
        if (existing2FA != null && existing2FA.getIsEnabled()) {
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_ALREADY_ENABLED);
        }

        OtpCode otp = otpService.generateOtp(
                user,
                user.getPhone(),
                user.getEmail(),
                OtpType.ENABLE_TWO_FACTOR,
                request.getIpAddress()
        );

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otp.getCode(),
                OtpType.ENABLE_TWO_FACTOR,
                request.getIpAddress(),
                null
        );

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otp.getExpiresAt())
                .message("OTP has been sent to your email to enable 2FA")
                .build();
    }

    @Transactional
    public Enable2FAResponse enable2FA(String userId, Enable2FARequest request) {
        User user = userValidationUtil.getUserById(userId);

        OtpCode otpCode = otpService.validateOtp(
                user.getPhone(),
                user.getEmail(),
                request.getOtp(),
                OtpType.ENABLE_TWO_FACTOR
        );

        String[] backupCodes = generateBackupCodes();
        String secretKey = generateSecretKey();

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElse(TwoFactorAuth.builder()
                        .userId(userId)
                        .user(user)
                        .backupCodesUsed(0)
                        .totalBackupCodes(BACKUP_CODES_COUNT)
                        .build());

        twoFactorAuth.enable();
        twoFactorAuth.setSecretKey(secretKey);
        twoFactorAuth.setBackupCodes(backupCodes);

        twoFactorAuthRepository.save(twoFactorAuth);
        otpService.markOtpAsUsed(otpCode);

        return Enable2FAResponse.builder()
                .enabled(true)
                .backupCodes(backupCodes)
                .message("Two-factor authentication enabled successfully. Please save your backup codes in a safe place.")
                .build();
    }

    @Transactional
    public OtpResponse request2FADisable(String userId, Request2FADisableOtpRequest request) {
        User user = userValidationUtil.getUserById(userId);
        userValidationUtil.requirePassword(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED));

        if (!twoFactorAuth.getIsEnabled()) {
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED);
        }

        OtpCode otp = otpService.generateOtp(
                user,
                user.getPhone(),
                user.getEmail(),
                OtpType.DISABLE_TWO_FACTOR,
                request.getIpAddress()
        );

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otp.getCode(),
                OtpType.DISABLE_TWO_FACTOR,
                request.getIpAddress(),
                null
        );

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otp.getExpiresAt())
                .message("OTP has been sent to your email to disable 2FA")
                .build();
    }

    @Transactional
    public void disable2FA(String userId, Disable2FARequest request) {
        User user = userValidationUtil.getUserById(userId);
        userValidationUtil.requirePassword(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }

        OtpCode otpCode = otpService.validateOtp(
                user.getPhone(),
                user.getEmail(),
                request.getOtp(),
                OtpType.DISABLE_TWO_FACTOR
        );

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED));

        twoFactorAuth.disable();
        twoFactorAuth.setSecretKey(null);
        twoFactorAuth.setBackupCodes(null);

        twoFactorAuthRepository.save(twoFactorAuth);
        otpService.markOtpAsUsed(otpCode);
    }

    public TwoFactorAuthStatus get2FAStatus(String userId) {
        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElse(null);

        if (twoFactorAuth == null || !twoFactorAuth.getIsEnabled()) {
            return TwoFactorAuthStatus.builder()
                    .enabled(false)
                    .build();
        }

        return TwoFactorAuthStatus.builder()
                .enabled(true)
                .enabledAt(twoFactorAuth.getEnabledAt())
                .lastUsedAt(twoFactorAuth.getLastUsedAt())
                .remainingBackupCodes(twoFactorAuth.getRemainingBackupCodes())
                .build();
    }

    public boolean is2FAEnabled(String userId) {
        return twoFactorAuthRepository.existsByUserIdAndIsEnabledTrue(userId);
    }

    private String[] generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        String[] codes = new String[BACKUP_CODES_COUNT];

        for (int i = 0; i < BACKUP_CODES_COUNT; i++) {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            codes[i] = Base64.getEncoder().withoutPadding()
                    .encodeToString(bytes)
                    .substring(0, 12)
                    .toUpperCase();
        }

        return codes;
    }

    private String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}