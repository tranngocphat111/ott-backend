package iuh.fit.userservice.service;

import iuh.fit.userservice.entity.OtpCode;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.OtpType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.repository.OtpCodeRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OtpService {

    final OtpCodeRepository otpCodeRepository;

    @Value("${otp.expiry-minutes:5}")
    int OTP_EXPIRY_MINUTES;

    @Value("${otp.length:6}")
    int OTP_LENGTH;

    @Value("${otp.max-attempts:5}")
    int MAX_OTP_ATTEMPTS;

    @Value("${otp.rate-limit-per-hour:5}")
    int RATE_LIMIT_PER_HOUR;

    @Transactional
    public OtpCode generateOtp(User user, String phone, String email, OtpType type, String ipAddress) {
        if (phone == null && email == null) throw new AppException(ErrorCode.INVALID_REQUEST);
        if (isEmailBasedOtpType(type) && email == null) throw new AppException(ErrorCode.EMAIL_REQUIRED);

        checkRateLimit(phone, email, type);
        invalidateOldOtps(phone, email, type);

        String code = generateRandomOtp(OTP_LENGTH);
        OtpCode otpCode = OtpCode.builder()
                .user(user).phone(phone).email(email).code(code).type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .ipAddress(ipAddress).isUsed(false).attempts(0).maxAttempts(MAX_OTP_ATTEMPTS)
                .build();
        return otpCodeRepository.save(otpCode);
    }

    @Transactional
    public OtpCode generateOtp(String phone, String email, OtpType type, String ipAddress) {
        return generateOtp(null, phone, email, type, ipAddress);
    }

    @Transactional
    public OtpCode validateOtp(String phone, String email, String code, OtpType type) {
        if (phone == null && email == null) throw new AppException(ErrorCode.INVALID_REQUEST);
        if (code == null || code.trim().isEmpty() || !code.matches("\\d+"))
            throw new AppException(ErrorCode.INVALID_OTP_CODE);

        OtpCode otpCode = findLatestValidOtp(phone, email, type);

        if (otpCode.getIsUsed()) throw new AppException(ErrorCode.OTP_ALREADY_USED);
        if (otpCode.isExpired()) throw new AppException(ErrorCode.OTP_EXPIRED);
        if (otpCode.isBlocked()) throw new AppException(ErrorCode.OTP_BLOCKED);

        if (!otpCode.getCode().equals(code.trim())) {
            otpCode.incrementAttempts();
            otpCodeRepository.save(otpCode);
            int remaining = otpCode.getMaxAttempts() - otpCode.getAttempts();
            if (remaining <= 0) throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            throw new AppException(ErrorCode.INVALID_OTP_CODE, "Invalid OTP. " + remaining + " attempts remaining.");
        }
        return otpCode;
    }

    @Transactional
    public void markOtpAsUsed(OtpCode otpCode) {
        if (otpCode.getIsUsed()) return;
        otpCode.setIsUsed(true);
        otpCode.setUsedAt(LocalDateTime.now());
        otpCodeRepository.save(otpCode);
    }

    @Transactional
    public void cleanupExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        List<OtpCode> expired = otpCodeRepository.findByExpiresAtBeforeAndIsUsedFalse(now);
        if (!expired.isEmpty()) {
            expired.forEach(o -> {
                o.setIsUsed(true);
                o.setUsedAt(now);
            });
            otpCodeRepository.saveAll(expired);
        }
    }

    private OtpCode findLatestValidOtp(String phone, String email, OtpType type) {
        LocalDateTime now = LocalDateTime.now();
        List<OtpCode> codes;
        if (phone != null) {
            codes = otpCodeRepository.findByPhoneAndTypeAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(phone, type, now);
        } else {
            codes = otpCodeRepository.findByEmailAndTypeAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, type, now);
        }
        if (codes.isEmpty()) throw new AppException(ErrorCode.OTP_NOT_FOUND);
        return codes.get(0);
    }

    private void checkRateLimit(String phone, String email, OtpType type) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long count = phone != null
                ? otpCodeRepository.countRecentOtpByPhone(phone, type, oneHourAgo)
                : otpCodeRepository.countRecentOtpByEmail(email, type, oneHourAgo);
        if (count >= RATE_LIMIT_PER_HOUR) throw new AppException(ErrorCode.OTP_RATE_LIMIT_EXCEEDED);
    }

    private void invalidateOldOtps(String phone, String email, OtpType type) {
        List<OtpCode> old = phone != null
                ? otpCodeRepository.findByPhoneAndTypeAndIsUsedFalse(phone, type)
                : otpCodeRepository.findByEmailAndTypeAndIsUsedFalse(email, type);
        if (!old.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            old.forEach(o -> {
                o.setIsUsed(true);
                o.setUsedAt(now);
            });
            otpCodeRepository.saveAll(old);
        }
    }

    private String generateRandomOtp(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) otp.append(random.nextInt(10));
        return otp.toString();
    }

    private boolean isEmailBasedOtpType(OtpType type) {
        return switch (type) {
            case LOGIN_OTP_EMAIL, EMAIL_VERIFICATION, RESET_PASSWORD,
                 CHANGE_EMAIL, CHANGE_PHONE, DELETE_ACCOUNT,
                 ENABLE_TWO_FACTOR, DISABLE_TWO_FACTOR -> true;
            default -> false;
        };
    }
}