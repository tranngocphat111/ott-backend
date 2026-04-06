package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.service.InternalUserService;
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
        return ApiResponse.<UserResponse>builder().result(internalUserService.createUser(
                (String) body.get("phone"), (String) body.get("email"), (String) body.get("googleId"),
                (String) body.get("fullName"), (String) body.get("avatarUrl"),
                (String) body.getOrDefault("accountType", "USER")
        )).build();
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
}