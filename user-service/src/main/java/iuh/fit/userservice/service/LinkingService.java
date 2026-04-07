package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.request.LinkEmailRequest;
import iuh.fit.userservice.dto.request.LinkPhoneRequest;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.entity.OtpCode;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.OtpType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkingService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final UserMapper userMapper;
    private final ValidationUtils validationUtils;

    @Transactional
    public UserResponse linkPhoneNumber(String userId, LinkPhoneRequest request) {
        log.info("Linking phone number for userId: {} | Phone: {}", userId, request.getPhone());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateStatus(user);

        if (user.getPhone() != null) {
            log.warn("Phone already linked for userId: {}", userId);
            throw new AppException(ErrorCode.PHONE_ALREADY_LINKED);
        }
        if (!validationUtils.isValidPhone(request.getPhone())) {
            log.warn("Invalid phone format for userId: {}", userId);
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        if (userRepository.existsByPhoneAndDeletedAtIsNull(request.getPhone())) {
            log.warn("Phone already exists in system: {}", request.getPhone());
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.validateOtp(request.getPhone(), null, request.getOtp(), OtpType.LINK_PHONE);

        user.setPhone(request.getPhone());
        user.setIsPhoneVerified(true);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user = userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);

        log.info("Phone number linked successfully for userId: {} | Phone: {}", userId, request.getPhone());

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse linkEmail(String userId, LinkEmailRequest request) {
        log.info("Linking email for userId: {} | Email: {}", userId, request.getEmail());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateStatus(user);

        if (user.getEmail() != null) {
            log.warn("Email already linked for userId: {}", userId);
            throw new AppException(ErrorCode.EMAIL_ALREADY_LINKED);
        }
        if (!validationUtils.isValidEmail(request.getEmail())) {
            log.warn("Invalid email format for userId: {}", userId);
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            log.warn("Email already exists in system: {}", request.getEmail());
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        OtpCode otpCode = otpService.validateOtp(null, request.getEmail(), request.getOtp(), OtpType.LINK_EMAIL);

        user.setEmail(request.getEmail());
        user.setIsEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user = userRepository.save(user);

        otpService.markOtpAsUsed(otpCode);

        log.info("Email linked successfully for userId: {} | Email: {}", userId, request.getEmail());

        return userMapper.toUserResponse(user);
    }

    private void validateStatus(User user) {
        if (user.getDeletedAt() != null) {
            log.warn("Deleted account attempted linking - userId: {}", user.getId());
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }
        if (!user.getIsActive()) {
            log.warn("Inactive account attempted linking - userId: {}", user.getId());
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }
        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("Blocked account attempted linking - userId: {}", user.getId());
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            log.info("Unblocking user due to expired block - userId: {}", user.getId());
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }
    }
}