package iuh.fit.authservice.service;

import iuh.fit.authservice.dto.event.AlertEmailEvent;
import iuh.fit.authservice.dto.event.OtpEmailEvent;
import iuh.fit.authservice.dto.event.WelcomeEmailEvent;
import iuh.fit.authservice.entity.enums.OtpType;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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

    public String getNotificationServiceUrl() {
        return notificationServiceUrl;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void sendOtpEmail(String email, String fullName, String otpCode,
                             OtpType otpType, String ipAddress, String location) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", internalApiKey);
            headers.set("Content-Type", "application/json");

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("email", email);
            body.put("fullName", fullName != null ? fullName : "User");
            body.put("otpType", otpType.name());
            body.put("ipAddress", ipAddress != null ? ipAddress : "");
            body.put("location", location != null ? location : "");

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
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void sendWelcomeEmailAsync(String userId, String email, String fullName,
                                      String phone, boolean hasPassword, boolean hasGoogleLinked) {
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
            log.info("Welcome email event sent for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send welcome email event for userId {}: {}", userId, e.getMessage());
        }
    }

    public void sendAlertEmailAsync(String userId, String email, String fullName,
                                    String alertType, String ipAddress, String location, String deviceInfo) {
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
            log.info("Alert email event sent for userId: {}, alertType: {}", userId, alertType);
        } catch (Exception e) {
            log.error("Failed to send alert email event: {}", e.getMessage());
        }
    }
}