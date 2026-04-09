package iuh.fit.userservice.service;

import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.auth.url}")
    private String authServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        return headers;
    }

    public void updateContact(String userId, String newPhone, String newEmail) {
        try {
            Map<String, String> body = new HashMap<>();
            if (newPhone != null) body.put("newPhone", newPhone);
            if (newEmail != null) body.put("newEmail", newEmail);

            restTemplate.exchange(
                    authServiceUrl + "/internal/users/" + userId + "/contact",
                    HttpMethod.PATCH,
                    new HttpEntity<>(body, internalHeaders()),
                    Void.class
            );
            log.info("Synced contact update to auth-service for userId: {}", userId);
        } catch (Exception e) {
            // Throw để rollback transaction bên user-service
            log.error("Failed to sync contact to auth-service for userId={}: {}", userId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public void syncUser(User user) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("id", user.getId());
            body.put("phone", user.getPhone());
            body.put("email", user.getEmail());
            body.put("fullName", user.getFullName());
            body.put("isActive", user.getIsActive());
            body.put("isBlocked", user.getIsBlocked());
            body.put("isFirstLogin", user.getIsFirstLogin());
            body.put("welcomeEmailSent", user.getWelcomeEmailSent());

            restTemplate.exchange(
                    authServiceUrl + "/internal/users/sync",
                    HttpMethod.POST,
                    new HttpEntity<>(body, internalHeaders()),
                    Void.class
            );
            log.info("User synced to auth-service successfully - userId: {}", user.getId());
        } catch (Exception e) {
            // Không rollback transaction register — sync thất bại không ảnh hưởng đăng ký
            log.warn("Failed to sync user to auth-service - userId: {}: {}", user.getId(), e.getMessage());
        }
    }
}
