package iuh.fit.ottbackend.service;

import iuh.fit.ottbackend.dto.request.RegisterRequest;
import iuh.fit.ottbackend.dto.request.RequestRegisterOtpRequest;
import iuh.fit.ottbackend.dto.response.OtpResponse;
import iuh.fit.ottbackend.dto.response.UserResponse;
import iuh.fit.ottbackend.entity.OtpCode;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.enums.AccountType;
import iuh.fit.ottbackend.entity.enums.OtpType;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.mapper.UserMapper;
import iuh.fit.ottbackend.repository.UserRepository;
import iuh.fit.ottbackend.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    UserMapper userMapper;
    OtpService otpService;
    EmailService emailService;
    ValidationUtils validationUtils;

    @Transactional
    public OtpResponse requestRegisterOtp(RequestRegisterOtpRequest request) {
        if (!validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new AppException(ErrorCode.FULL_NAME_REQUIRED);
        }

        String sanitizedName = validationUtils.sanitizeString(request.getFullName());
        if (sanitizedName.length() > 100) {
            throw new AppException(ErrorCode.INVALID_FULL_NAME);
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.generateOtp(
                request.getPhone(),
                request.getEmail(),
                OtpType.REGISTER,
                request.getIpAddress()
        );


        emailService.sendOtpEmail(
                request.getEmail(),
                sanitizedName,
                otpCode.getCode(),
                OtpType.REGISTER,
                request.getIpAddress(),
                request.getLocation()
        );

        return OtpResponse.builder()
                .phone(request.getPhone())
                .email(validationUtils.maskEmail(request.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your email")
                .build();
    }


    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Validate input
        if (!validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        if (!validationUtils.isValidPassword(request.getPassword())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }

        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new AppException(ErrorCode.FULL_NAME_REQUIRED);
        }

        String sanitizedName = validationUtils.sanitizeString(request.getFullName());
        if (sanitizedName.length() > 100) {
            throw new AppException(ErrorCode.INVALID_FULL_NAME);
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.validateOtp(
                request.getPhone(),
                request.getEmail(),
                request.getOtp(),
                OtpType.REGISTER
        );

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .phone(request.getPhone())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(sanitizedName)
                .accountType(AccountType.USER)
                .isPhoneVerified(true)
                .phoneVerifiedAt(now)
                .isEmailVerified(true)
                .emailVerifiedAt(now)
                .isActive(true)
                .isBlocked(false)
                .isFirstLogin(true)
                .welcomeEmailSent(false)
                .build();

        user = userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);

        return userMapper.toUserResponse(user);
    }
}