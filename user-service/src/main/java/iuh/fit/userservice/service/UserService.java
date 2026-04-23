package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.event.UserCreatedEvent;
import iuh.fit.userservice.dto.request.RegisterRequest;
import iuh.fit.userservice.dto.request.RequestRegisterOtpRequest;
import iuh.fit.userservice.dto.request.UpdateContactRequest;
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
import iuh.fit.userservice.utils.UserValidationUtil;
import iuh.fit.userservice.utils.ValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    UserValidationUtil userValidationUtil;
    AuthServiceClient authServiceClient;
    UserEventPublisher userEventPublisher;

    @NonFinal
    @Value("${aws.s3.default-avatar}")
    String defaultAvatarUrl;

    @NonFinal
    @Value("${aws.s3.default-cover-photo}")
    String defaultCoverPhotoUrl;

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

        Optional<User> deletedByPhone = userRepository.findByPhone(request.getPhone());
        if (deletedByPhone.isPresent() && deletedByPhone.get().getDeletedAt() != null) {
            User deletedUser = deletedByPhone.get();
            log.info("Hard deleting soft-deleted user by phone to allow new registration: {}", deletedUser.getId());
            userRepository.delete(deletedUser);
            entityManager.flush();
        }

        Optional<User> deletedByEmail = userRepository.findByEmail(request.getEmail());
        if (deletedByEmail.isPresent() && deletedByEmail.get().getDeletedAt() != null) {
            User deletedUser = deletedByEmail.get();
            log.info("Hard deleting soft-deleted user by email to allow new registration: {}", deletedUser.getId());
            userRepository.delete(deletedUser);
            entityManager.flush();
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
                .avatarUrl(defaultAvatarUrl)
                .coverUrl(defaultCoverPhotoUrl)
                .build();

        user = userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);

        authServiceClient.syncUser(user);

        notificationPublisher.publishUserRegisteredEvent(user.getId(), "password");

        // Welcome email async
        notificationPublisher.sendWelcomeEmailAsync(user);

        // Publish event for Chat service (One time, with phone)
        userEventPublisher.publishUserCreated(UserCreatedEvent.builder()
                .userId(user.getId())
                .username(user.getFullName())
                .avatar(user.getAvatarUrl())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build());

        return userMapper.toUserResponse(user);
    }

    private String buildUsername(User user) {
        String base = null;
        if (user.getEmail() != null && user.getEmail().contains("@")) {
            base = user.getEmail().substring(0, user.getEmail().indexOf('@'));
        } else if (user.getFullName() != null) {
            base = user.getFullName();
        }

        if (base == null || base.isBlank()) {
            return "user";
        }

        String normalized = validationUtils.sanitizeString(base)
                .replace(" ", "")
                .toLowerCase();

        if (normalized.isBlank()) {
            return "user";
        }

        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    public void updateContact(String userId, UpdateContactRequest request) {
        User user = userValidationUtil.getUserById(userId);
        if (request.getNewPhone() != null) {
            user.setPhone(request.getNewPhone());
            user.setPhoneChangedAt(LocalDateTime.now());
        }
        if (request.getNewEmail() != null) {
            user.setEmail(request.getNewEmail());
            user.setEmailChangedAt(LocalDateTime.now());
        }
        userRepository.save(user);
    }
}