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
                        .message("AUTH_SERVICE_UNAVAILABLE")
                        .build());
    }

    @RequestMapping("/user")
    public ResponseEntity<ApiResponse<Void>> userFallback() {
        log.warn("Circuit breaker triggered for user-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5002)
                        .message("USER_SERVICE_UNAVAILABLE")
                        .build());
    }

    @RequestMapping("/notification")
    public ResponseEntity<ApiResponse<Void>> notificationFallback() {
        log.warn("Circuit breaker triggered for notification-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5003)
                        .message("NOTIFICATION_SERVICE_UNAVAILABLE")
                        .build());
    }

    @RequestMapping("/media")
    public ResponseEntity<ApiResponse<Void>> mediaFallback() {
        log.warn("Circuit breaker triggered for media-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5003)
                        .message("MEDIA_SERVICE_UNAVAILABLE")
                        .build());
    }

    @RequestMapping("/chat")
    public ResponseEntity<ApiResponse<Void>> chatFallback() {
        log.warn("Circuit breaker triggered for chat-service or ai-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5005)
                        .message("CHAT_SERVICE_UNAVAILABLE")
                        .build());
    }

    @RequestMapping("/analytic")
    public ResponseEntity<ApiResponse<Void>> analyticFallback() {
        log.warn("Circuit breaker triggered for analytic-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5004)
                        .message("ANALYTIC_SERVICE_UNAVAILABLE")
                        .build());
    }

    @RequestMapping("/moderation")
    public ResponseEntity<ApiResponse<Void>> moderationFallback() {
        log.warn("Circuit breaker triggered for moderation-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .code(5006)
                        .message("MODERATION_SERVICE_UNAVAILABLE")
                        .build());
    }
}
