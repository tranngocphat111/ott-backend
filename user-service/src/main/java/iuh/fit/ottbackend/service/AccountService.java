package iuh.fit.ottbackend.service;

import iuh.fit.ottbackend.dto.request.*;
import iuh.fit.ottbackend.dto.response.*;
import iuh.fit.ottbackend.entity.OtpCode;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.enums.OtpType;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.utils.UserValidationUtil;
import iuh.fit.ottbackend.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final SessionService sessionService;
    private final ValidationUtils validationUtils;
    private final UserValidationUtil userValidationUtil;


    @Transactional
    public void setPassword(String userId, SetPasswordRequest request) {
        User user = userValidationUtil.getUserById(userId);

        if (user.getPasswordHash() != null) {
            throw new AppException(ErrorCode.PASSWORD_ALREADY_SET);
        }

        if (!validationUtils.isValidPassword(request.getPassword())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);
    }

    @Transactional
    public PasswordChangeResponse changePassword(String userId, ChangePasswordRequest request) {
        User user = userValidationUtil.getUserById(userId);
        userValidationUtil.requirePassword(user);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }

        if (!validationUtils.isValidPassword(request.getNewPassword())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);


        int revokedCount = sessionService.revokeAllUserSessions(user.getId(), "Password changed");

        return PasswordChangeResponse.builder()
                .success(true)
                .message("Password changed successfully")
                .sessionsRevoked(revokedCount)
                .build();
    }

    @Transactional
    public OtpResponse requestPasswordReset(ForgotPasswordRequest request) {
        if (request.getPhone() == null && request.getEmail() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        if (request.getEmail() != null && !validationUtils.isValidEmail(request.getEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        if (request.getPhone() != null && !validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        User user = userValidationUtil.findUserByPhoneOrEmail(request.getPhone(), request.getEmail());

        String emailToSend = determineEmailForOtp(request.getEmail(), user.getEmail());
        if (emailToSend == null) {
            throw new AppException(ErrorCode.EMAIL_REQUIRED_FOR_PASSWORD_RESET);
        }

        OtpCode otpCode = otpService.generateOtp(
                user,
                request.getPhone(),
                emailToSend,
                OtpType.RESET_PASSWORD,
                request.getIpAddress()
        );

        emailService.sendOtpEmail(
                emailToSend,
                user.getFullName(),
                otpCode.getCode(),
                OtpType.RESET_PASSWORD,
                request.getIpAddress(),
                null
        );

        return OtpResponse.builder()
                .phone(request.getPhone())
                .email(validationUtils.maskEmail(emailToSend))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your email address")
                .build();
    }

    @Transactional
    public void verifyPasswordReset(VerifyPasswordResetRequest request) {
        if (request.getPhone() == null && request.getEmail() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        if (request.getEmail() != null && !validationUtils.isValidEmail(request.getEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        if (request.getPhone() != null && !validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        User user = userValidationUtil.findUserByPhoneOrEmail(request.getPhone(), request.getEmail());

        OtpCode otpCode = otpService.validateOtp(
                request.getPhone(),
                request.getEmail(),
                request.getOtp(),
                OtpType.RESET_PASSWORD
        );

        if (!validationUtils.isValidPassword(request.getNewPassword())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);
        sessionService.revokeAllUserSessions(user.getId(), "Password reset");

    }

    @Transactional
    public OtpResponse requestChangeEmail(String userId, RequestChangeEmailOtpRequest request) {
        User user = userValidationUtil.getUserById(userId);

        if (!validationUtils.isValidEmail(request.getNewEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        if (user.getEmail().equalsIgnoreCase(request.getNewEmail())) {
            throw new AppException(ErrorCode.SAME_EMAIL);
        }

        if (userValidationUtil.userRepository.existsByEmail(request.getNewEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.generateOtp(
                user,
                null,
                user.getEmail(),
                OtpType.CHANGE_EMAIL,
                request.getIpAddress()
        );

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otpCode.getCode(),
                OtpType.CHANGE_EMAIL,
                request.getIpAddress(),
                null
        );


        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your current email")
                .build();
    }

    @Transactional
    public EmailChangeResponse changeEmail(String userId, ChangeEmailRequest request) {
        User user = userValidationUtil.getUserById(userId);

        if (!validationUtils.isValidEmail(request.getNewEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        if (user.getEmail().equalsIgnoreCase(request.getNewEmail())) {
            throw new AppException(ErrorCode.SAME_EMAIL);
        }

        if (userValidationUtil.userRepository.existsByEmail(request.getNewEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.validateOtp(
                null,
                user.getEmail(),
                request.getOtp(),
                OtpType.CHANGE_EMAIL
        );

        String oldEmail = user.getEmail();
        user.setEmail(request.getNewEmail());
        user.setEmailChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);

        int revokedCount = sessionService.revokeAllUserSessions(user.getId(), "Email changed");


        return EmailChangeResponse.builder()
                .success(true)
                .newEmail(request.getNewEmail())
                .message("Email changed successfully")
                .sessionsRevoked(revokedCount)
                .build();
    }


    @Transactional
    public OtpResponse requestChangePhone(String userId, RequestChangePhoneOtpRequest request) {
        User user = userValidationUtil.getUserById(userId);

        if (!validationUtils.isValidPhone(request.getNewPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        if (user.getPhone().equals(request.getNewPhone())) {
            throw new AppException(ErrorCode.SAME_PHONE);
        }

        if (userValidationUtil.userRepository.existsByPhone(request.getNewPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.generateOtp(
                user,
                request.getNewPhone(),
                user.getEmail(),
                OtpType.CHANGE_PHONE,
                request.getIpAddress()
        );

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otpCode.getCode(),
                OtpType.CHANGE_PHONE,
                request.getIpAddress(),
                null
        );

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your email")
                .build();
    }

    @Transactional
    public PhoneChangeResponse changePhone(String userId, ChangePhoneRequest request) {
        User user = userValidationUtil.getUserById(userId);

        if (!validationUtils.isValidPhone(request.getNewPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        if (user.getPhone().equals(request.getNewPhone())) {
            throw new AppException(ErrorCode.SAME_PHONE);
        }

        if (userValidationUtil.userRepository.existsByPhone(request.getNewPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.validateOtp(
                request.getNewPhone(),
                user.getEmail(),
                request.getOtp(),
                OtpType.CHANGE_PHONE
        );

        String oldPhone = user.getPhone();
        user.setPhone(request.getNewPhone());
        user.setPhoneChangedAt(LocalDateTime.now());
        userValidationUtil.userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);

        int revokedCount = sessionService.revokeAllUserSessions(user.getId(), "Phone changed");

        return PhoneChangeResponse.builder()
                .success(true)
                .newPhone(request.getNewPhone())
                .message("Phone number changed successfully")
                .sessionsRevoked(revokedCount)
                .build();
    }


    @Transactional
    public OtpResponse requestDeleteAccount(String userId, RequestDeleteAccountOtpRequest request) {
        User user = userValidationUtil.getUserById(userId);

        OtpCode otpCode = otpService.generateOtp(
                user,
                user.getPhone(),
                user.getEmail(),
                OtpType.DELETE_ACCOUNT,
                request.getIpAddress()
        );

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otpCode.getCode(),
                OtpType.DELETE_ACCOUNT,
                request.getIpAddress(),
                null
        );


        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your email to confirm account deletion")
                .build();
    }

    @Transactional
    public AccountDeletionResponse deleteAccount(String userId, DeleteAccountRequest request) {
        User user = userValidationUtil.getUserById(userId);

        if (userValidationUtil.hasPassword(user)) {
            if (request.getPassword() == null) {
                throw new AppException(ErrorCode.PASSWORD_REQUIRED);
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new AppException(ErrorCode.INCORRECT_PASSWORD);
            }
        }

        OtpCode otpCode = otpService.validateOtp(
                user.getPhone(),
                user.getEmail(),
                request.getOtp(),
                OtpType.DELETE_ACCOUNT
        );

        LocalDateTime now = LocalDateTime.now();
        user.setDeletedAt(now);
        user.setIsActive(false);
        userValidationUtil.userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);
        sessionService.revokeAllUserSessions(userId, "Account deleted");

        return AccountDeletionResponse.builder()
                .success(true)
                .message("Account deleted successfully")
                .deletedAt(now)
                .build();
    }

    private String determineEmailForOtp(String requestEmail, String userEmail) {
        if (requestEmail != null) {
            if (userEmail != null && !requestEmail.equalsIgnoreCase(userEmail)) {
                throw new AppException(ErrorCode.EMAIL_MISMATCH);
            }
            return requestEmail;
        }
        return userEmail;
    }
}