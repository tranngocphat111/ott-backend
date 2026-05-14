package iuh.fit.authservice.controller;

import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import iuh.fit.authservice.service.UserServiceClient;
import iuh.fit.authservice.service.UserSyncService;
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

    private final UserSyncService userSyncService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    private void validateKey(String key) {
        if (!internalApiKey.equals(key)) throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    @PostMapping("/sync")
    public void syncUser(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Internal-Key") String key) {
        validateKey(key);

        String userId = (String) body.get("id");

        // Dùng lại logic đã có trong UserSyncService
        UserServiceClient.UserDto dto = new UserServiceClient.UserDto();
        dto.setId(userId);
        dto.setPhone((String) body.get("phone"));
        dto.setEmail((String) body.get("email"));
        dto.setFullName((String) body.get("fullName"));
        dto.setIsActive((Boolean) body.get("isActive"));
        dto.setIsBlocked((Boolean) body.get("isBlocked"));
        dto.setIsFirstLogin((Boolean) body.get("isFirstLogin"));
        dto.setWelcomeEmailSent((Boolean) body.get("welcomeEmailSent"));

        userSyncService.ensureUserExists(dto);
        log.info("User sync triggered from user-service for userId: {}", userId);
    }
}