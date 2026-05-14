package iuh.fit.authservice.service;

import iuh.fit.authservice.dto.event.AlertEmailEvent;
import iuh.fit.authservice.dto.event.WelcomeEmailEvent;
import iuh.fit.authservice.entity.enums.OtpType;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${internal.notification-service-url}")
    private String notificationServiceUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Value("${analytics.queue.user-login:analytics.user.login.queue}")
    private String userLoginAnalyticsQueue;

    public String getNotificationServiceUrl() {
        return notificationServiceUrl;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void sendOtpEmail(String email, String fullName, String otpCode,
                             OtpType otpType, String ipAddress, String location) {
        log.info("Sending OTP email to: {} | Type: {}", email, otpType);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", internalApiKey);
            headers.set("Content-Type", "application/json");

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("toEmail", email);
            body.put("toName", fullName != null ? fullName : "User");
            body.put("otpType", otpType.name());
            body.put("ipAddress", ipAddress != null ? ipAddress : "");
            body.put("location", location != null ? location : "");

            body.put("otpCode", "");

            if (otpCode != null) {
                body.put("otpCode", otpCode);
            }

            restTemplate.exchange(
                    notificationServiceUrl + "/internal/notification/otp/send",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Void.class
            );

            log.info("OTP email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {} | Type: {}", email, otpType, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void sendWelcomeEmailAsync(String userId, String email, String fullName,
                                      String phone, boolean hasPassword, boolean hasGoogleLinked) {
        log.info("Publishing welcome email event for userId: {}", userId);

        try {
            WelcomeEmailEvent event = WelcomeEmailEvent.builder()
                    .userId(userId)
                    .email(email)
                    .fullName(fullName)
                    .phone(phone)
                    .hasPassword(hasPassword)
                    .hasGoogleLinked(hasGoogleLinked)
                    .build();

            rabbitTemplate.convertAndSend(exchange, "notification.welcome", event);
            log.debug("Welcome email event published successfully for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish welcome email event for userId: {}", userId, e);
        }
    }

    public void sendAlertEmailAsync(String userId, String email, String fullName,
                                    String alertType, String ipAddress, String location, String deviceInfo) {
        log.info("Publishing alert email event for userId: {} | AlertType: {}", userId, alertType);

        try {
            AlertEmailEvent event = AlertEmailEvent.builder()
                    .userId(userId)
                    .email(email)
                    .fullName(fullName)
                    .alertType(alertType)
                    .ipAddress(ipAddress)
                    .location(location)
                    .deviceInfo(deviceInfo)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")))
                    .build();

            rabbitTemplate.convertAndSend(exchange, "notification.alert", event);
            log.debug("Alert email event published successfully for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish alert email event for userId: {} | AlertType: {}", userId, alertType, e);
        }
    }

    public void publishUserLoginEvent(String userId, String loginMethod) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event_id", UUID.randomUUID().toString());
            event.put("user_id", userId);
            event.put("login_method", loginMethod);
            event.put("timestamp", Instant.now());

            rabbitTemplate.convertAndSend(userLoginAnalyticsQueue, event);
            log.info("User login analytics event published for userId={}, method={}", userId, loginMethod);
        } catch (Exception e) {
            // Do not break login flow if analytics is unavailable
            log.warn("Failed to publish user.login analytics event for userId={}: {}", userId, e.getMessage());
        }
    }

    public void publishUserLogoutEvent(String userId, String sessionId, String deviceId, String action, java.util.List<String> revokedDeviceIds) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId);
            event.put("sessionId", sessionId);
            event.put("deviceId", deviceId);
            event.put("action", action);
            if (revokedDeviceIds != null) {
                event.put("revokedDeviceIds", revokedDeviceIds);
            }

            // Publish to user.events exchange with routing key user.logout
            rabbitTemplate.convertAndSend("user.events", "user.logout", event);
            log.info("User logout event published for userId={}, action={}", userId, action);
        } catch (Exception e) {
            log.warn("Failed to publish user.logout event for userId={}: {}", userId, e.getMessage());
        }
    }
}