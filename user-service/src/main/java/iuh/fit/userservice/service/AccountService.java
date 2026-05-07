package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.request.*;
import iuh.fit.userservice.dto.response.*;
import iuh.fit.userservice.entity.OtpCode;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.OtpType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.utils.UserValidationUtil;
import iuh.fit.userservice.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final NotificationPublisher notificationPublisher;
    private final SessionService sessionService;
    private final ValidationUtils validationUtils;
    private final UserValidationUtil userValidationUtil;
    private final UserPhotoService userPhotoService;
    private final UserEventPublisher userEventPublisher;

    @Transactional
    public void setPassword(String userId, SetPasswordRequest request) {
        log.info("Setting initial password for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);
        if (user.getPasswordHash() != null) {
            log.warn("Password already set for userId: {}", userId);
            throw new AppException(ErrorCode.PASSWORD_ALREADY_SET);
        }
        if (!validationUtils.isValidPassword(request.getPassword())) {
            log.warn("Invalid password format for userId: {}", userId);
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);

        log.info("Initial password set successfully for userId: {}", userId);

    }

    @Transactional
    public PasswordChangeResponse changePassword(String userId, ChangePasswordRequest request) {
        log.info("Password change requested for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);
        userValidationUtil.requirePassword(user);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            log.warn("Incorrect old password for userId: {}", userId);
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }
        if (!validationUtils.isValidPassword(request.getNewPassword())) {
            log.warn("Invalid new password format for userId: {}", userId);
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            log.warn("New password is same as old password for userId: {}", userId);
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);

        int revoked = sessionService.revokeAllUserSessions(user.getId(), "Password changed");
        notificationPublisher.sendAlertEmailAsync(user, "PASSWORD_CHANGED", request.getIpAddress(), null,
                request.getDeviceInfo());

        log.info("Password changed successfully for userId: {} | Sessions revoked: {}", userId, revoked);

        return PasswordChangeResponse.builder()
                .success(true)
                .message("Password changed successfully")
                .sessionsRevoked(revoked)
                .build();
    }

    @Transactional
    public OtpResponse requestPasswordReset(ForgotPasswordRequest request) {
        log.info("Password reset requested for phone: {}, email: {}", request.getPhone(), request.getEmail());

        if (request.getPhone() == null || request.getEmail() == null)
            throw new AppException(ErrorCode.PHONE_AND_EMAIL_REQUIRED);
        if (!validationUtils.isValidEmail(request.getEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (!validationUtils.isValidPhone(request.getPhone()))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);

        User user = userValidationUtil.findUserByPhoneOrEmail(request.getPhone(), request.getEmail());

        if (!user.getPhone().equals(request.getPhone()))
            throw new AppException(ErrorCode.PHONE_MISMATCH);
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_MISMATCH);

        OtpCode otpCode = otpService.generateOtp(user, request.getPhone(), user.getEmail(), OtpType.RESET_PASSWORD,
                request.getIpAddress());
        notificationPublisher.sendOtpEmail(user.getEmail(), user.getFullName(), otpCode.getCode(),
                OtpType.RESET_PASSWORD, request.getIpAddress(), null, user.getId());

        log.info("Password reset OTP sent successfully to email: {}", user.getEmail());

        return OtpResponse.builder()
                .phone(validationUtils.maskPhone(request.getPhone()))
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP sent to your email")
                .build();
    }

    @Transactional
    public void verifyPasswordReset(VerifyPasswordResetRequest request) {
        log.info("Verifying password reset for phone: {}, email: {}", request.getPhone(), request.getEmail());

        if (request.getPhone() == null || request.getEmail() == null)
            throw new AppException(ErrorCode.PHONE_AND_EMAIL_REQUIRED);
        if (!validationUtils.isValidEmail(request.getEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (!validationUtils.isValidPhone(request.getPhone()))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);

        User user = userValidationUtil.findUserByPhoneOrEmail(request.getPhone(), request.getEmail());
        if (!user.getPhone().equals(request.getPhone()))
            throw new AppException(ErrorCode.PHONE_MISMATCH);
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_MISMATCH);
        if (!validationUtils.isValidPassword(request.getNewPassword()))
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);

        if (user.getPasswordHash() != null &&
                passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        OtpCode otpCode = otpService.validateOtp(request.getPhone(), request.getEmail(), request.getOtp(),
                OtpType.RESET_PASSWORD);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);
        sessionService.revokeAllUserSessions(user.getId(), "Password reset");

        log.info("Password reset completed successfully for userId: {}", user.getId());
    }

    @Transactional
    public OtpResponse requestChangeEmail(String userId, RequestChangeEmailOtpRequest request) {
        log.info("Change email OTP requested for userId: {}, newEmail: {}", userId, request.getNewEmail());

        User user = userValidationUtil.getUserById(userId);
        if (!validationUtils.isValidEmail(request.getNewEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(request.getNewEmail()))
            throw new AppException(ErrorCode.SAME_EMAIL);
        if (userValidationUtil.userRepository.existsByEmailAndDeletedAtIsNull(request.getNewEmail()))
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);

        OtpCode otpCode = otpService.generateOtp(user, null, user.getEmail(), OtpType.CHANGE_EMAIL,
                request.getIpAddress());
        notificationPublisher.sendOtpEmail(user.getEmail(), user.getFullName(), otpCode.getCode(), OtpType.CHANGE_EMAIL,
                request.getIpAddress(), null, userId);

        log.info("Change email OTP sent successfully to: {}", user.getEmail());

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP sent to current email")
                .build();
    }

    @Transactional
    public EmailChangeResponse changeEmail(String userId, ChangeEmailRequest request) {
        log.info("Changing email for userId: {} to {}", userId, request.getNewEmail());

        User user = userValidationUtil.getUserById(userId);
        if (!validationUtils.isValidEmail(request.getNewEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(request.getNewEmail()))
            throw new AppException(ErrorCode.SAME_EMAIL);
        if (userValidationUtil.userRepository.existsByEmailAndDeletedAtIsNull(request.getNewEmail()))
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);

        OtpCode otpCode = otpService.validateOtp(null, user.getEmail(), request.getOtp(), OtpType.CHANGE_EMAIL);

        boolean googleUnlinked = false;
        if (user.getGoogleId() != null) {
            log.info("Unlinking Google account due to email change - userId: {}", userId);
            user.setGoogleId(null);
            googleUnlinked = true;
        }

        user.setEmail(request.getNewEmail());
        user.setEmailChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);

        userEventPublisher.publishUserUpdated(
                iuh.fit.userservice.dto.event.UserUpdatedEvent.builder()
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .coverUrl(user.getCoverUrl())
                        .bio(user.getBio())
                        .work(user.getWork())
                        .location(user.getLocation())
                        .relationshipStatus(user.getRelationshipStatus())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .build());

        int revoked = sessionService.revokeAllUserSessions(user.getId(), "Email changed");
        notificationPublisher.sendAlertEmailAsync(user, "EMAIL_CHANGED", request.getIpAddress(), null, null);

        log.info("Email changed successfully for userId: {} | Google unlinked: {}", userId, googleUnlinked);

        String msg = "Email changed successfully." + (googleUnlinked ? " Google account unlinked." : "");
        return EmailChangeResponse.builder()
                .success(true)
                .newEmail(request.getNewEmail())
                .googleUnlinked(googleUnlinked)
                .message(msg)
                .sessionsRevoked(revoked)
                .build();
    }

    @Transactional
    public OtpResponse requestChangePhone(String userId, RequestChangePhoneOtpRequest request) {
        String normalizedNewPhone = validationUtils.normalizePhone(request.getNewPhone());
        log.info("Change phone OTP requested for userId: {}, newPhone: {}", userId, normalizedNewPhone);

        User user = userValidationUtil.getUserById(userId);
        if (!validationUtils.isValidPhone(normalizedNewPhone))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        if (user.getPhone().equals(normalizedNewPhone))
            throw new AppException(ErrorCode.SAME_PHONE);
        if (userValidationUtil.userRepository.existsByPhoneAndDeletedAtIsNull(normalizedNewPhone))
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);

        OtpCode otpCode = otpService.generateOtp(user, normalizedNewPhone, user.getEmail(), OtpType.CHANGE_PHONE,
                request.getIpAddress());
        notificationPublisher.sendOtpEmail(user.getEmail(), user.getFullName(), otpCode.getCode(), OtpType.CHANGE_PHONE,
                request.getIpAddress(), null, userId);

        log.info("Change phone OTP sent successfully to email of userId: {}", userId);

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP sent to email")
                .build();
    }

    @Transactional
    public PhoneChangeResponse changePhone(String userId, ChangePhoneRequest request) {
        String normalizedNewPhone = validationUtils.normalizePhone(request.getNewPhone());
        log.info("Changing phone for userId: {} to {}", userId, normalizedNewPhone);

        User user = userValidationUtil.getUserById(userId);
        if (!validationUtils.isValidPhone(normalizedNewPhone))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        if (user.getPhone().equals(normalizedNewPhone))
            throw new AppException(ErrorCode.SAME_PHONE);
        if (userValidationUtil.userRepository.existsByPhoneAndDeletedAtIsNull(normalizedNewPhone))
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);

        OtpCode otpCode = otpService.validateOtp(normalizedNewPhone, user.getEmail(), request.getOtp(),
                OtpType.CHANGE_PHONE);

        user.setPhone(normalizedNewPhone);
        user.setPhoneChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);

        userEventPublisher.publishUserUpdated(
                iuh.fit.userservice.dto.event.UserUpdatedEvent.builder()
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .coverUrl(user.getCoverUrl())
                        .bio(user.getBio())
                        .work(user.getWork())
                        .location(user.getLocation())
                        .relationshipStatus(user.getRelationshipStatus())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .build());

        int revoked = sessionService.revokeAllUserSessions(user.getId(), "Phone changed");

        log.info("Phone changed successfully for userId: {} | Sessions revoked: {}", userId, revoked);

        return PhoneChangeResponse.builder()
                .success(true)
                .newPhone(normalizedNewPhone)
                .message("Phone changed successfully")
                .sessionsRevoked(revoked)
                .build();
    }

    @Transactional
    public OtpResponse requestDeleteAccount(String userId, RequestDeleteAccountOtpRequest request) {
        log.info("Account deletion OTP requested for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);

        if (userValidationUtil.hasPassword(user)) {
            if (request.getPassword() == null)
                throw new AppException(ErrorCode.PASSWORD_REQUIRED);
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }
        OtpCode otpCode = otpService.generateOtp(user, user.getPhone(), user.getEmail(), OtpType.DELETE_ACCOUNT,
                request.getIpAddress());
        notificationPublisher.sendOtpEmail(user.getEmail(), user.getFullName(), otpCode.getCode(),
                OtpType.DELETE_ACCOUNT, request.getIpAddress(), null, userId);

        log.info("Account deletion OTP sent successfully to userId: {}", userId);

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP sent for account deletion confirmation")
                .build();
    }

    @Transactional
    public AccountDeletionResponse deleteAccount(String userId, DeleteAccountRequest request) {
        log.info("Account deletion requested for userId: {}", userId);

        User user = userValidationUtil.getUserById(userId);

        OtpCode otpCode = otpService.validateOtp(user.getPhone(), user.getEmail(), request.getOtp(),
                OtpType.DELETE_ACCOUNT);

        userPhotoService.deleteAllUserPhotos(userId);

        LocalDateTime now = LocalDateTime.now();
        String suffix = "_deleted_" + now.toEpochSecond(ZoneOffset.UTC);
        user.setPhone(user.getPhone() + suffix);
        if (user.getEmail() != null)
            user.setEmail(user.getEmail() + suffix);
        if (user.getGoogleId() != null)
            user.setGoogleId(user.getGoogleId() + suffix);
        user.setDeletedAt(now);
        user.setIsActive(false);
        userValidationUtil.userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);
        sessionService.revokeAllUserSessions(userId, "Account deleted");

        log.info("Account deleted successfully for userId: {}", userId);

        return AccountDeletionResponse.builder()
                .success(true)
                .message("Account deleted. You can create a new account with the same phone/email.")
                .deletedAt(now)
                .build();
    }

    public void verifyForgotPasswordOtp(VerifyForgotOtpRequest request) {
        if (!validationUtils.isValidEmail(request.getEmail()))
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (!validationUtils.isValidPhone(request.getPhone()))
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);

        User user = userValidationUtil.findUserByPhoneOrEmail(request.getPhone(), request.getEmail());
        if (!user.getPhone().equals(request.getPhone()))
            throw new AppException(ErrorCode.PHONE_MISMATCH);
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_MISMATCH);

        // Chỉ validate, không mark as used
        otpService.validateOtp(request.getPhone(), user.getEmail(), request.getOtp(), OtpType.RESET_PASSWORD);
    }
}