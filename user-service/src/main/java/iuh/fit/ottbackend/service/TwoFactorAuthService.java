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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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

    @PersistenceContext
    private EntityManager entityManager;

    private static final int BACKUP_CODES_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 12;

    @Transactional
    public OtpResponse request2FAEnable(String userId, Request2FAEnableOtpRequest request) {
        User user = userValidationUtil.getUserById(userId);

        // ✅ BẮT BUỘC PHẢI CÓ PASSWORD MỚI BẬT ĐƯỢC 2FA
        if (user.getPasswordHash() == null) {
            throw new AppException(ErrorCode.PASSWORD_REQUIRED_FOR_2FA);
        }

        // Kiểm tra xem đã enable chưa
        TwoFactorAuth existing2FA = twoFactorAuthRepository.findByUserId(userId).orElse(null);
        if (existing2FA != null && existing2FA.getIsEnabled()) {
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_ALREADY_ENABLED);
        }

        // Generate OTP
        OtpCode otp = otpService.generateOtp(
                user,
                user.getPhone(),
                user.getEmail(),
                OtpType.ENABLE_TWO_FACTOR,
                request.getIpAddress()
        );

        // Send email
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

        // Validate OTP
        OtpCode otpCode = otpService.validateOtp(
                user.getPhone(),
                user.getEmail(),
                request.getOtp(),
                OtpType.ENABLE_TWO_FACTOR
        );

        // Generate codes
        String[] backupCodes = generateBackupCodes();
        String secretKey = generateSecretKey();
        LocalDateTime now = LocalDateTime.now();

        // Chuyển array sang PostgreSQL text[]
        String backupCodesStr = "{\"" + String.join("\",\"", backupCodes) + "\"}";

        // XÓA record cũ
        entityManager.createNativeQuery("DELETE FROM two_factor_auth WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();

        // INSERT - dùng CAST riêng
        entityManager.createNativeQuery(
                        "INSERT INTO two_factor_auth (user_id, is_enabled, secret_key, backup_codes, " +
                                "enabled_at, backup_codes_used, total_backup_codes, created_at, updated_at) " +
                                "VALUES (?1, ?2, ?3, CAST(?4 AS text[]), ?5, ?6, ?7, ?8, ?9)"
                )
                .setParameter(1, userId)
                .setParameter(2, true)
                .setParameter(3, secretKey)
                .setParameter(4, backupCodesStr)
                .setParameter(5, now)
                .setParameter(6, 0)
                .setParameter(7, BACKUP_CODES_COUNT)
                .setParameter(8, now)
                .setParameter(9, now)
                .executeUpdate();

        entityManager.flush();

        // Mark OTP as used
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

        // ✅ BẮT BUỘC PHẢI CÓ PASSWORD
        userValidationUtil.requirePassword(user);

        // ✅ VERIFY PASSWORD TRƯỚC KHI GỬI OTP
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

        // ✅ VERIFY PASSWORD LẦN NỮA KHI DISABLE
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
            codes[i] = generateSingleBackupCode(random);
        }

        return codes;
    }

    private String generateSingleBackupCode(SecureRandom random) {
        String chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder code = new StringBuilder(BACKUP_CODE_LENGTH);

        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            int index = random.nextInt(chars.length());
            code.append(chars.charAt(index));
        }

        return code.toString();
    }

    private String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}