package iuh.fit.apigateway.controller;

import iuh.fit.apigateway.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @RequestMapping("/auth")
    public ResponseEntity<ApiResponse<Void>> authFallback() {
        log.warn("Circuit breaker triggered for auth-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5001)
                        .message("Auth service is currently unavailable. Please try again later.")
                        .build());
    }

    @RequestMapping("/user")
    public ResponseEntity<ApiResponse<Void>> userFallback() {
        log.warn("Circuit breaker triggered for user-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5002)
                        .message("User service is currently unavailable. Please try again later.")
                        .build());
    }

    @RequestMapping("/notification")
    public ResponseEntity<ApiResponse<Void>> notificationFallback() {
        log.warn("Circuit breaker triggered for notification-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5003)
                        .message("Notification service is currently unavailable. Please try again later.")
                        .build());
    }
}