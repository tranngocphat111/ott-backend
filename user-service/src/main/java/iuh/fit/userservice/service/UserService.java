package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.request.RegisterRequest;
import iuh.fit.userservice.dto.request.RequestRegisterOtpRequest;
import iuh.fit.userservice.dto.response.OtpResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.entity.OtpCode;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.entity.enums.OtpType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.utils.ValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserService {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    UserMapper userMapper;
    OtpService otpService;
    NotificationPublisher notificationPublisher;
    ValidationUtils validationUtils;
    EntityManager entityManager;

    @Transactional
    public OtpResponse requestRegisterOtp(RequestRegisterOtpRequest request) {
        if (!validationUtils.isValidPhone(request.getPhone()))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        if (!validationUtils.isValidEmail(request.getEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (request.getFullName() == null || request.getFullName().trim().isEmpty())
            throw new AppException(ErrorCode.FULL_NAME_REQUIRED);

        String sanitizedName = validationUtils.sanitizeString(request.getFullName());
        if (sanitizedName.length() > 100) throw new AppException(ErrorCode.INVALID_FULL_NAME);

        if (userRepository.existsByPhoneAndDeletedAtIsNull(request.getPhone()))
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);

        OtpCode otpCode = otpService.generateOtp(
                request.getPhone(), request.getEmail(), OtpType.REGISTER, request.getIpAddress());

        notificationPublisher.sendOtpEmail(
                request.getEmail(), sanitizedName, otpCode.getCode(),
                OtpType.REGISTER, request.getIpAddress(), request.getLocation(), null);

        return OtpResponse.builder()
                .phone(request.getPhone())
                .email(validationUtils.maskEmail(request.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your email")
                .build();
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!validationUtils.isValidPhone(request.getPhone()))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        if (!validationUtils.isValidEmail(request.getEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (!validationUtils.isValidPassword(request.getPassword()))
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        if (request.getFullName() == null || request.getFullName().trim().isEmpty())
            throw new AppException(ErrorCode.FULL_NAME_REQUIRED);

        String sanitizedName = validationUtils.sanitizeString(request.getFullName());
        if (sanitizedName.length() > 100) throw new AppException(ErrorCode.INVALID_FULL_NAME);

        if (userRepository.existsByPhoneAndDeletedAtIsNull(request.getPhone()))
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);

        // Handle soft-deleted account trùng phone
        Optional<User> deletedByPhone = userRepository.findByPhone(request.getPhone());
        if (deletedByPhone.isPresent() && deletedByPhone.get().getDeletedAt() != null) {
            User deletedUser = deletedByPhone.get();
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            if (deletedUser.getDeletedAt().isAfter(thirtyDaysAgo)) {
                throw new AppException(ErrorCode.ACCOUNT_CAN_BE_RESTORED,
                        "Your account was recently deleted and can still be restored.");
            } else {
                log.info("🗑️ Hard deleting old account to release phone: {}", deletedUser.getId());
                userRepository.delete(deletedUser);
                entityManager.flush();
            }
        }

        OtpCode otpCode = otpService.validateOtp(
                request.getPhone(), request.getEmail(), request.getOtp(), OtpType.REGISTER);

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .phone(request.getPhone()).email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(sanitizedName).accountType(AccountType.USER)
                .isPhoneVerified(true).phoneVerifiedAt(now)
                .isEmailVerified(true).emailVerifiedAt(now)
                .isActive(true).isBlocked(false)
                .isFirstLogin(true).welcomeEmailSent(false)
                .build();

        user = userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);

        // Welcome email async
        notificationPublisher.sendWelcomeEmailAsync(user);

        return userMapper.toUserResponse(user);
    }
}