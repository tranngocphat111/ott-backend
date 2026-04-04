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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${internal.user-service-url}")
    private String userServiceUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    // DTO nhỏ gọn để map response từ user-service
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
        private Boolean twoFactorEnabled;
        private LocalDate dateOfBirth;
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
        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            return extractResult(response);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling user-service getUserById: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public UserDto getUserByPhone(String phone) {
        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/by-phone/" + phone,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            return extractResult(response);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling user-service getUserByPhone: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public UserDto getUserByEmail(String email) {
        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/by-email/" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            return extractResult(response);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling user-service getUserByEmail: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public UserDto getUserByGoogleId(String googleId) {
        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/by-google/" + googleId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            return extractResult(response);
        } catch (HttpClientErrorException.NotFound e) {
            return null; // User not found by googleId is OK (new user)
        } catch (Exception e) {
            log.error("Error calling user-service getUserByGoogleId: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public String getPasswordHash(String userId) {
        try {
            ResponseEntity<ApiResponse<String>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId + "/password-hash",
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<String>>() {}
            );
            return extractResult(response);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (Exception e) {
            log.error("Error calling user-service getPasswordHash: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public void updateLastLogin(String userId) {
        try {
            restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId + "/last-login",
                    HttpMethod.PATCH,
                    new HttpEntity<>(internalHeaders()),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to update lastLoginAt for userId={}: {}", userId, e.getMessage());
        }
    }

    public boolean existsByPhone(String phone) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/exists/phone/" + phone,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
            );
            Boolean result = extractResult(response);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error checking phone existence: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public boolean existsByEmail(String email) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/exists/email/" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
            );
            Boolean result = extractResult(response);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error checking email existence: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    public boolean existsByGoogleId(String googleId) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/exists/google/" + googleId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
            );
            Boolean result = extractResult(response);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error checking googleId existence: {}", e.getMessage());
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
    }

    public UserDto createUser(CreateUserRequest createRequest) {
        try {
            ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, internalHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto>>() {}
            );
            return extractResult(response);
        } catch (Exception e) {
            log.error("Error calling user-service createUser: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
    }

    private <T> T extractResult(ResponseEntity<ApiResponse<T>> response) {
        if (response.getBody() == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVICE_ERROR);
        }
        return response.getBody().getResult();
    }
}