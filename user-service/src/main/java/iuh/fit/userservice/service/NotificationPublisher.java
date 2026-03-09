package iuh.fit.userservice.service;

import iuh.fit.userservice.config.RabbitMQConfig;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.OtpType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


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

    public void sendOtpEmail(String toEmail, String toName, String otpCode,
                             OtpType otpType, String ipAddress, String location,
                             String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Key", internalApiKey);

            Map<String, Object> body = Map.of(
                    "toEmail",   toEmail,
                    "toName",    toName != null ? toName : "User",
                    "otpCode",   otpCode,
                    "otpType",   otpType.name(),
                    "ipAddress", ipAddress != null ? ipAddress : "",
                    "location",  location  != null ? location  : "",
                    "userId",    userId    != null ? userId    : ""
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(notificationServiceUrl + "/internal/notification/otp/send", request, Void.class);
            log.info("OTP email request sent for: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send OTP email for {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendWelcomeEmailAsync(User user) {
        try {
            Map<String, Object> event = Map.of(
                    "toEmail",        user.getEmail() != null ? user.getEmail() : "",
                    "toName",         user.getFullName(),
                    "phone",          user.getPhone(),
                    "hasPassword",    user.getPasswordHash() != null,
                    "hasGoogleLinked",user.getGoogleId() != null,
                    "userId",         user.getId()
            );
            rabbitTemplate.convertAndSend(rabbitMQConfig.exchange, rabbitMQConfig.welcomeRoutingKey, event);
            log.info("Welcome email event published for userId: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to publish welcome email event: {}", e.getMessage());
        }
    }

    public void sendAlertEmailAsync(User user, String alertType, String ipAddress,
                                    String location, String deviceInfo) {
        try {
            Map<String, Object> event = Map.of(
                    "toEmail",    user.getEmail() != null ? user.getEmail() : "",
                    "toName",     user.getFullName(),
                    "alertType",  alertType,
                    "ipAddress",  ipAddress  != null ? ipAddress  : "",
                    "location",   location   != null ? location   : "",
                    "deviceInfo", deviceInfo != null ? deviceInfo : "",
                    "userId",     user.getId()
            );
            rabbitTemplate.convertAndSend(rabbitMQConfig.exchange, rabbitMQConfig.alertRoutingKey, event);
            log.info("Alert email event published for userId: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to publish alert email event: {}", e.getMessage());
        }
    }
}