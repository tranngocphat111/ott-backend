package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.entity.enums.DeviceType;
import iuh.fit.userservice.entity.enums.LoginMethod;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationPublisher notificationPublisher;
    private final SessionService sessionService;

    public UserResponse getUserById(String userId) {
        User user = userRepository.findByIdWithTwoFactorAuth(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    public UserResponse getUserByPhone(String phone) {
        User user = userRepository.findByPhoneWithTwoFactorAuth(phone)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmailWithTwoFactorAuth(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    public UserResponse getUserByGoogleId(String googleId) {
        User user = userRepository.findByGoogleIdWithTwoFactorAuth(googleId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    public String getPasswordHash(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return user.getPasswordHash();
    }

    public void updateLastLogin(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(user.getIsFirstLogin())) user.setIsFirstLogin(false);
            userRepository.save(user);
        });
    }

    public UserResponse createUser(String phone, String email, String googleId,
                                   String fullName, String avatarUrl, String accountTypeStr) {
        User user = User.builder()
                .phone(phone).email(email).googleId(googleId)
                .fullName(fullName).avatarUrl(avatarUrl)
                .accountType(AccountType.valueOf(accountTypeStr))
                .isPhoneVerified(phone != null).isEmailVerified(email != null)
                .isActive(true).isBlocked(false)
                .isFirstLogin(true).welcomeEmailSent(false)
                .build();

        if (phone != null) user.setPhoneVerifiedAt(LocalDateTime.now());
        if (email != null) user.setEmailVerifiedAt(LocalDateTime.now());

        user = userRepository.save(user);
        notificationPublisher.sendWelcomeEmailAsync(user);
        log.info("User created via internal API: {}", user.getId());
        return userMapper.toUserResponse(user);
    }

    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhoneAndDeletedAtIsNull(phone);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailAndDeletedAtIsNull(email);
    }

    public boolean existsByGoogleId(String googleId) {
        return userRepository.existsByGoogleIdAndDeletedAtIsNull(googleId);
    }

    public void createSession(String userId, String deviceId, String deviceName,
                              String ipAddress, String userAgent, String sessionToken,
                              String refreshToken, String loginMethodStr, String deviceTypeStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        sessionService.createUserSession(
                user, deviceId, DeviceType.valueOf(deviceTypeStr), deviceName,
                ipAddress, userAgent, sessionToken, refreshToken,
                LoginMethod.valueOf(loginMethodStr)
        );
    }
}
