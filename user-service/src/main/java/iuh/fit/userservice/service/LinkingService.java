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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        validateStatus(user);

        if (user.getPhone() != null) throw new AppException(ErrorCode.PHONE_ALREADY_LINKED);
        if (!validationUtils.isValidPhone(request.getPhone())) throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        if (userRepository.existsByPhoneAndDeletedAtIsNull(request.getPhone()))
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);

        OtpCode otpCode = otpService.validateOtp(request.getPhone(), null, request.getOtp(), OtpType.LINK_PHONE);
        user.setPhone(request.getPhone());
        user.setIsPhoneVerified(true);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user = userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);
        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse linkEmail(String userId, LinkEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        validateStatus(user);

        if (user.getEmail() != null) throw new AppException(ErrorCode.EMAIL_ALREADY_LINKED);
        if (!validationUtils.isValidEmail(request.getEmail())) throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);

        OtpCode otpCode = otpService.validateOtp(null, request.getEmail(), request.getOtp(), OtpType.LINK_EMAIL);
        user.setEmail(request.getEmail());
        user.setIsEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user = userRepository.save(user);
        otpService.markOtpAsUsed(otpCode);
        return userMapper.toUserResponse(user);
    }

    private void validateStatus(User user) {
        if (user.getDeletedAt() != null) throw new AppException(ErrorCode.ACCOUNT_DELETED);
        if (!user.getIsActive()) throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now()))
                throw new AppException(ErrorCode.USER_BLOCKED);
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }
    }
}