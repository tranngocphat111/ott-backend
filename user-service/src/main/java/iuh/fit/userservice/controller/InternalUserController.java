package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.entity.enums.DeviceType;
import iuh.fit.userservice.entity.enums.LoginMethod;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.service.NotificationPublisher;
import iuh.fit.userservice.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationPublisher notificationPublisher;
    private final SessionService sessionService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    private void validateKey(String key) {
        if (!internalApiKey.equals(key)) throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    @GetMapping("/by-phone/{phone}")
    public ApiResponse<UserResponse> getByPhone(
            @PathVariable String phone,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        User user = userRepository.findByPhoneAndDeletedAtIsNull(phone)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @GetMapping("/by-email/{email}")
    public ApiResponse<UserResponse> getByEmail(
            @PathVariable String email,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @GetMapping("/by-google/{googleId}")
    public ApiResponse<UserResponse> getByGoogleId(
            @PathVariable String googleId,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        User user = userRepository.findByGoogleIdAndDeletedAtIsNull(googleId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getById(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @PostMapping("/validate-credentials")
    public ApiResponse<UserResponse> validateCredentials(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        String phone = body.get("phone");
        String passwordHash = body.get("passwordHash");

        User user = userRepository.findByPhoneAndDeletedAtIsNull(phone)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @GetMapping("/{userId}/password-hash")
    public ApiResponse<String> getPasswordHash(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.<String>builder().result(user.getPasswordHash()).build();
    }

    @PatchMapping("/{userId}/last-login")
    public ApiResponse<Void> updateLastLogin(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(user.getIsFirstLogin())) user.setIsFirstLogin(false);
            userRepository.save(user);
        });
        return ApiResponse.<Void>builder().message("Last login updated").build();
    }

    @PostMapping
    public ApiResponse<UserResponse> createUser(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);

        String phone    = (String) body.get("phone");
        String email    = (String) body.get("email");
        String googleId = (String) body.get("googleId");
        String fullName = (String) body.get("fullName");
        String avatarUrl= (String) body.get("avatarUrl");
        String accountTypeStr = (String) body.getOrDefault("accountType", "USER");

        User user = User.builder()
                .phone(phone).email(email).googleId(googleId).fullName(fullName)
                .avatarUrl(avatarUrl)
                .accountType(AccountType.valueOf(accountTypeStr))
                .isPhoneVerified(phone != null).isEmailVerified(email != null)
                .isActive(true).isBlocked(false)
                .isFirstLogin(true).welcomeEmailSent(false)
                .build();

        if (phone   != null) user.setPhoneVerifiedAt(LocalDateTime.now());
        if (email   != null) user.setEmailVerifiedAt(LocalDateTime.now());

        user = userRepository.save(user);
        notificationPublisher.sendWelcomeEmailAsync(user);
        log.info("User created via internal API: {}", user.getId());
        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @GetMapping("/exists/phone/{phone}")
    public ApiResponse<Boolean> existsByPhone(
            @PathVariable String phone,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<Boolean>builder()
                .result(userRepository.existsByPhoneAndDeletedAtIsNull(phone)).build();
    }

    @GetMapping("/exists/email/{email}")
    public ApiResponse<Boolean> existsByEmail(
            @PathVariable String email,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<Boolean>builder()
                .result(userRepository.existsByEmailAndDeletedAtIsNull(email)).build();
    }

    @GetMapping("/exists/google/{googleId}")
    public ApiResponse<Boolean> existsByGoogleId(
            @PathVariable String googleId,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<Boolean>builder()
                .result(userRepository.existsByGoogleIdAndDeletedAtIsNull(googleId)).build();
    }

    @PostMapping("/sessions")
    public ApiResponse<Void> createSession(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);

        String userId     = (String) body.get("userId");
        String deviceId   = (String) body.get("deviceId");
        String deviceName = (String) body.get("deviceName");
        String ipAddress  = (String) body.get("ipAddress");
        String userAgent  = (String) body.get("userAgent");
        String sessionToken   = (String) body.get("sessionToken");
        String refreshToken   = (String) body.get("refreshToken");
        String loginMethodStr = (String) body.getOrDefault("loginMethod", "LOCAL");
        String deviceTypeStr  = (String) body.getOrDefault("deviceType", "UNKNOWN");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        sessionService.createUserSession(
                user,
                deviceId,
                DeviceType.valueOf(deviceTypeStr),
                deviceName,
                ipAddress,
                userAgent,
                sessionToken,
                refreshToken,
                LoginMethod.valueOf(loginMethodStr)
        );

        return ApiResponse.<Void>builder().message("Session created").build();
    }
}