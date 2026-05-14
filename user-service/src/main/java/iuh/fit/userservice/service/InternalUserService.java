package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.event.UserCreatedEvent;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.entity.TwoFactorAuth;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.entity.enums.DeviceType;
import iuh.fit.userservice.entity.enums.LoginMethod;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.TwoFactorAuthRepository;
import iuh.fit.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationPublisher notificationPublisher;
    private final SessionService sessionService;
    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;

    @Value("${aws.s3.default-avatar}")
    private String defaultAvatarUrl;

    @Value("${aws.s3.default-cover-photo}")
    private String defaultCoverPhotoUrl;

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
                                   String fullName, String avatarUrl, String coverPhotoUrl,
                                   String accountTypeStr) {
        User user = User.builder()
                .phone(phone).email(email).googleId(googleId)
                .fullName(fullName)
                .avatarUrl(avatarUrl != null && !avatarUrl.isBlank() ? avatarUrl : defaultAvatarUrl)
                .coverUrl(coverPhotoUrl != null && !coverPhotoUrl.isBlank() ? coverPhotoUrl : defaultCoverPhotoUrl)
                .accountType(AccountType.valueOf(accountTypeStr))
                .isPhoneVerified(phone != null).isEmailVerified(email != null)
                .isActive(true).isBlocked(false)
                .isFirstLogin(true).welcomeEmailSent(false)
                .build();

        if (phone != null) user.setPhoneVerifiedAt(LocalDateTime.now());
        if (email != null) user.setEmailVerifiedAt(LocalDateTime.now());

        user = userRepository.save(user);
        notificationPublisher.sendWelcomeEmailAsync(user);

        // Publish event for Chat service
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getId())
                .username(user.getFullName())
                .avatar(user.getAvatarUrl())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
        userEventPublisher.publishUserCreated(event);

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

    public boolean validateAndConsumeBackupCode(String userId, String inputCode) {
        TwoFactorAuth twoFA = twoFactorAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED));

        if (!Boolean.TRUE.equals(twoFA.getIsEnabled())) {
            throw new AppException(ErrorCode.TWO_FACTOR_AUTH_NOT_ENABLED);
        }

        String[] codes = twoFA.getBackupCodes();
        if (codes == null || codes.length == 0) {
            throw new AppException(ErrorCode.BACKUP_CODE_EXHAUSTED);
        }

        String matchedHash = Arrays.stream(codes)
                .filter(hash -> passwordEncoder.matches(inputCode, hash))
                .findFirst()
                .orElse(null);

        if (matchedHash == null) {
            log.warn("Invalid backup code attempt for userId: {}", userId);
            return false;
        }

        String[] remaining = Arrays.stream(codes)
                .filter(hash -> !hash.equals(matchedHash))
                .toArray(String[]::new);

        twoFA.setBackupCodes(remaining);
        twoFA.setBackupCodesUsed(twoFA.getBackupCodesUsed() + 1);
        twoFA.setLastUsedAt(LocalDateTime.now());
        twoFactorAuthRepository.save(twoFA);

        log.info("Backup code consumed for userId: {} | Remaining: {}", userId, remaining.length);
        return true;
    }
}
