package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.UpdateContactRequest;
import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.service.InternalUserService;
import iuh.fit.userservice.service.UserService;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final InternalUserService internalUserService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
        private final NotificationPublisher notificationPublisher;

    @Value("${internal.api.key}")
    private String internalApiKey;

    private void validateKey(String key) {
        if (!internalApiKey.equals(key)) throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getById(@PathVariable String userId, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<UserResponse>builder().result(internalUserService.getUserById(userId)).build();
    }

    @GetMapping("/by-phone/{phone}")
    public ApiResponse<UserResponse> getByPhone(@PathVariable String phone, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<UserResponse>builder().result(internalUserService.getUserByPhone(phone)).build();
    }

    @GetMapping("/by-email/{email}")
    public ApiResponse<UserResponse> getByEmail(@PathVariable String email, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<UserResponse>builder().result(internalUserService.getUserByEmail(email)).build();
    }

    @GetMapping("/by-google/{googleId}")
    public ApiResponse<UserResponse> getByGoogleId(@PathVariable String googleId, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<UserResponse>builder().result(internalUserService.getUserByGoogleId(googleId)).build();
    }

    @GetMapping("/{userId}/password-hash")
    public ApiResponse<String> getPasswordHash(@PathVariable String userId, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<String>builder().result(internalUserService.getPasswordHash(userId)).build();
    }

    @PatchMapping("/{userId}/last-login")
    public ApiResponse<Void> updateLastLogin(@PathVariable String userId, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        internalUserService.updateLastLogin(userId);
        return ApiResponse.<Void>builder().message("Last login updated").build();
    }

    @PostMapping
    public ApiResponse<UserResponse> createUser(@RequestBody Map<String, Object> body, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        String coverPhotoUrl = (String) body.get("coverPhotoUrl");
        if (coverPhotoUrl == null) {
            coverPhotoUrl = (String) body.get("coverUrl");
        }
        return ApiResponse.<UserResponse>builder().result(internalUserService.createUser(
                (String) body.get("phone"), (String) body.get("email"), (String) body.get("googleId"),
                (String) body.get("fullName"), (String) body.get("avatarUrl"), coverPhotoUrl,
                (String) body.getOrDefault("accountType", "USER")
        )).build();

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
        log.info("User created via internal API: {}", user.getId());

        String registerMethod = (googleId != null && !googleId.isBlank()) ? "google" : "internal";
        notificationPublisher.publishUserRegisteredEvent(user.getId(), registerMethod);

        return ApiResponse.<UserResponse>builder().result(userMapper.toUserResponse(user)).build();
    }

    @GetMapping("/exists/phone/{phone}")
    public ApiResponse<Boolean> existsByPhone(@PathVariable String phone, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<Boolean>builder().result(internalUserService.existsByPhone(phone)).build();
    }

    @GetMapping("/exists/email/{email}")
    public ApiResponse<Boolean> existsByEmail(@PathVariable String email, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<Boolean>builder().result(internalUserService.existsByEmail(email)).build();
    }

    @GetMapping("/exists/google/{googleId}")
    public ApiResponse<Boolean> existsByGoogleId(@PathVariable String googleId, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        return ApiResponse.<Boolean>builder().result(internalUserService.existsByGoogleId(googleId)).build();
    }

    @PostMapping("/sessions")
    public ApiResponse<Void> createSession(@RequestBody Map<String, Object> body, @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        internalUserService.createSession(
                (String) body.get("userId"), (String) body.get("deviceId"), (String) body.get("deviceName"),
                (String) body.get("ipAddress"), (String) body.get("userAgent"),
                (String) body.get("sessionToken"), (String) body.get("refreshToken"),
                (String) body.getOrDefault("loginMethod", "LOCAL"),
                (String) body.getOrDefault("deviceType", "UNKNOWN")
        );
        return ApiResponse.<Void>builder().message("Session created").build();
    }

    @PostMapping("/{userId}/backup-code/validate")
    public ApiResponse<Boolean> validateBackupCode(
            @PathVariable String userId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        String code = body.get("code");
        boolean valid = internalUserService.validateAndConsumeBackupCode(userId, code);
        if (!valid) throw new AppException(ErrorCode.INVALID_BACKUP_CODE);
        return ApiResponse.<Boolean>builder().result(true).build();
    }

    @PatchMapping("/{userId}/contact")
    public ApiResponse<Void> updateContact(
            @PathVariable String userId,
            @RequestBody UpdateContactRequest request,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);
        userService.updateContact(userId, request);
        return ApiResponse.<Void>builder().message("Contact updated").build();
    }

}