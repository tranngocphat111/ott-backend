package iuh.fit.userservice.service;

import iuh.fit.userservice.config.RabbitMQConfig;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.OtpType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final RestTemplate restTemplate;

    @Value("${services.notification.url}")
    private String notificationServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Value("${analytics.queue.user-registered:analytics.user.registered.queue}")
    private String userRegisteredAnalyticsQueue;

    @Async
    public void sendOtpEmail(String toEmail, String toName, String otpCode,
                             OtpType otpType, String ipAddress, String location,
                             String userId) {

        log.info("Sending OTP email request asynchronously to: {} | Type: {}", toEmail, otpType);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Key", internalApiKey);

            Map<String, Object> body = Map.of(
                    "toEmail", toEmail,
                    "toName", toName != null ? toName : "User",
                    "otpCode", otpCode,
                    "otpType", otpType.name(),
                    "ipAddress", ipAddress != null ? ipAddress : "",
                    "location", location != null ? location : "",
                    "userId", userId != null ? userId : ""
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(
                    notificationServiceUrl + "/internal/notification/otp/send",
                    request,
                    Void.class
            );

            log.info("OTP email request sent successfully to: {} | Type: {}", toEmail, otpType);

        } catch (Exception e) {
            log.error("Failed to send OTP email to: {} | Type: {}", toEmail, otpType, e);
        }
    }

    public void sendWelcomeEmailAsync(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send welcome email: user or email is null/empty");
            return;
        }

        log.info("Publishing welcome email event for userId: {}", user.getId());

        try {
            Map<String, Object> event = Map.of(
                    "userId", user.getId(),
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "fullName", user.getFullName(),
                    "phone", user.getPhone(),
                    "hasPassword", user.getPasswordHash() != null,
                    "hasGoogleLinked", user.getGoogleId() != null
            );

            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.exchange,
                    rabbitMQConfig.welcomeRoutingKey,
                    event
            );

            log.debug("Welcome email event published successfully for userId: {}", user.getId());

        } catch (Exception e) {
            log.error("Failed to publish welcome email event for userId: {}", user.getId(), e);
        }
    }

    public void sendAlertEmailAsync(User user, String alertType, String ipAddress,
                                    String location, String deviceInfo) {

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send alert email: user or email is null/empty | AlertType: {}", alertType);
            return;
        }

        log.info("Publishing alert email event - userId: {} | AlertType: {}", user.getId(), alertType);

        try {
            Map<String, Object> event = Map.of(
                    "toEmail", user.getEmail() != null ? user.getEmail() : "",
                    "toName", user.getFullName(),
                    "alertType", alertType,
                    "ipAddress", ipAddress != null ? ipAddress : "",
                    "location", location != null ? location : "",
                    "deviceInfo", deviceInfo != null ? deviceInfo : "",
                    "userId", user.getId()
            );

            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.exchange,
                    rabbitMQConfig.alertRoutingKey,
                    event
            );

            log.debug("Alert email event published successfully for userId: {}", user.getId());

        } catch (Exception e) {
            log.error("Failed to publish alert email event for userId: {} | AlertType: {}",
                    user.getId(), alertType, e);
        }
    }

    public void publishUserRegisteredEvent(String userId, String registerMethod) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event_id", UUID.randomUUID().toString());
            event.put("user_id", userId);
            event.put("register_method", registerMethod);
            event.put("timestamp", Instant.now());

            rabbitTemplate.convertAndSend(userRegisteredAnalyticsQueue, event);
            log.info("User registered analytics event published for userId={}, method={}", userId, registerMethod);
        } catch (Exception e) {
            // Do not break registration flow if analytics pipeline is unavailable
            log.warn("Failed to publish user.registered analytics event for userId={}: {}", userId, e.getMessage());
        }
    }
}