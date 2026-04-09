package iuh.fit.authservice.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${internal.user-service-url}")
    private String userServiceUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserDto {
        private String id;
        private String phone;
        private String email;
        private String googleId;
        private String fullName;
        private String avatarUrl;
        private String accountType;
        private Boolean isActive;
        private Boolean isBlocked;
        private LocalDateTime blockedUntil;
        private String blockedReason;
        private LocalDateTime deletedAt;
        private Boolean isFirstLogin;
        private Boolean welcomeEmailSent;
        @JsonAlias({"is2FAEnabled", "2FAEnabled"})
        private Boolean twoFactorEnabled;
        private LocalDate dateOfBirth;
        @JsonAlias("coverUrl")
        private String coverUrl;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T result;
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        return headers;
    }

    public UserDto getUserById(String userId) {
        log.debug("Calling user-service to get user by id: {}", userId);

        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            UserDto user = extractResult(response);
            log.info("Retrieved user by id: {} successfully", userId);
            log.error("🔥 RAW USER RESPONSE = {}", response.getBody());
            return user;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found by id: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling user-service getUserById for userId={}", userId, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public UserDto getUserByPhone(String phone) {
        log.debug("Calling user-service to get user by phone: {}", phone);

        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/by-phone/" + phone,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            UserDto user = extractResult(response);
            log.info("Retrieved user by phone successfully");
            return user;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found by phone: {}", phone);
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling user-service getUserByPhone for phone={}", phone, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public UserDto getUserByEmail(String email) {
        log.debug("Calling user-service to get user by email: {}", email);

        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/by-email/" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            UserDto user = extractResult(response);
            log.info("Retrieved user by email successfully");
            return user;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found by email: {}", email);
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling user-service getUserByEmail for email={}", email, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public UserDto getUserByGoogleId(String googleId) {
        log.debug("Calling user-service to get user by googleId: {}", googleId);

        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/by-google/" + googleId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            UserDto user = extractResult(response);
            log.info("Retrieved user by googleId successfully");
            return user;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("User not found by googleId: {} (this is normal for new users)", googleId);
            return null;
        } catch (Exception e) {
            log.error("Error calling user-service getUserByGoogleId for googleId={}", googleId, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public String getPasswordHash(String userId) {
        log.debug("Calling user-service to get password hash for userId: {}", userId);

        try {
            ResponseEntity<ApiResponse<String>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId + "/password-hash",
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<String>>() {}
            );
            String hash = extractResult(response);
            log.debug("Password hash retrieved successfully for userId: {}", userId);
            return hash;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found when getting password hash, userId: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (Exception e) {
            log.error("Error calling user-service getPasswordHash for userId={}", userId, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public void updateLastLogin(String userId) {
        log.debug("Updating last login time for userId: {}", userId);

        try {
            restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId + "/last-login",
                    HttpMethod.PATCH,
                    new HttpEntity<>(internalHeaders()),
                    Void.class
            );
            log.debug("Last login updated successfully for userId: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to update lastLoginAt for userId={}: {}", userId, e.getMessage());
        }
    }

    public boolean existsByPhone(String phone) {
        log.debug("Checking phone existence: {}", phone);

        try {
            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/exists/phone/" + phone,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
            );
            Boolean result = extractResult(response);
            boolean exists = Boolean.TRUE.equals(result);
            log.debug("Phone existence check: {} -> {}", phone, exists);
            return exists;
        } catch (Exception e) {
            log.error("Error checking phone existence for phone={}", phone, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public boolean existsByEmail(String email) {
        log.debug("Checking email existence: {}", email);

        try {
            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/exists/email/" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
            );
            Boolean result = extractResult(response);
            boolean exists = Boolean.TRUE.equals(result);
            log.debug("Email existence check: {} -> {}", email, exists);
            return exists;
        } catch (Exception e) {
            log.error("Error checking email existence for email={}", email, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public boolean existsByGoogleId(String googleId) {
        log.debug("Checking googleId existence: {}", googleId);

        try {
            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/exists/google/" + googleId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
            );
            Boolean result = extractResult(response);
            boolean exists = Boolean.TRUE.equals(result);
            log.debug("GoogleId existence check: {} -> {}", googleId, exists);
            return exists;
        } catch (Exception e) {
            log.error("Error checking googleId existence for googleId={}", googleId, e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateUserRequest {
        private String phone;
        private String email;
        private String googleId;
        private String fullName;
        private String avatarUrl;
        private String coverPhotoUrl;
    }

    public UserDto createUser(CreateUserRequest createRequest) {
        log.info("Creating new user via user-service - phone: {}, email: {}",
                createRequest.getPhone(), createRequest.getEmail());

        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            UserDto user = extractResult(response);
            log.info("User created successfully via user-service - userId: {}", user.getId());
            return user;
        } catch (Exception e) {
            log.error("Error calling user-service createUser", e);
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    private <T> T extractResult(ResponseEntity<ApiResponse<T>> response) {
        if (response.getBody() == null || response.getBody().getResult() == null) {
            log.warn("Received empty result from user-service");
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
        return response.getBody().getResult();
    }

    public void createSession(String userId, String deviceId, String deviceName,
                              String ipAddress, String userAgent,
                              String sessionToken, String refreshToken,
                              String loginMethod, String deviceType) {
        log.debug("Creating session via user-service for userId: {}", userId);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("deviceId", deviceId);
            body.put("deviceName", deviceName);
            body.put("ipAddress", ipAddress);
            body.put("userAgent", userAgent);
            body.put("sessionToken", sessionToken);
            body.put("refreshToken", refreshToken);
            body.put("loginMethod", loginMethod != null ? loginMethod : "LOCAL");
            body.put("deviceType", deviceType != null ? deviceType : "UNKNOWN");

            restTemplate.exchange(
                    userServiceUrl + "/internal/users/sessions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, internalHeaders()),
                    Void.class
            );
            log.info("Session created via user-service for userId: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to create session in user-service for userId={}: {}", userId, e.getMessage());
        }
    }

    public boolean validateAndConsumeBackupCode(String userId, String code) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("code", code);

            restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId + "/backup-code/validate",
                    HttpMethod.POST,
                    new HttpEntity<>(body, internalHeaders()),
                    Void.class
            );
            return true;
        } catch (HttpClientErrorException e) {
            return false;
        } catch (Exception e) {
            log.error("Error validating backup code for userId={}", userId, e);
            return false;
        }
    }

    public void updateContact(String userId, String newPhone, String newEmail) {
        try {
            Map<String, String> body = new HashMap<>();
            if (newPhone != null) body.put("newPhone", newPhone);
            if (newEmail != null) body.put("newEmail", newEmail);

            restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId + "/contact",
                    HttpMethod.PATCH,
                    new HttpEntity<>(body, internalHeaders()),
                    Void.class
            );
            log.info("Contact updated in user-service for userId: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to sync contact update for userId={}: {}", userId, e.getMessage());
        }
    }
}