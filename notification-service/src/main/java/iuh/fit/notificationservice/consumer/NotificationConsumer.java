package iuh.fit.notificationservice.consumer;

import iuh.fit.notificationservice.dto.event.AlertEmailEvent;
import iuh.fit.notificationservice.dto.event.OtpEmailEvent;
import iuh.fit.notificationservice.dto.event.WelcomeEmailEvent;
import iuh.fit.notificationservice.dto.event.InAppNotificationEvent;
import iuh.fit.notificationservice.entity.enums.OtpType;
import iuh.fit.notificationservice.service.EmailService;
import iuh.fit.notificationservice.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final EmailService emailService;
    private final InAppNotificationService inAppNotificationService;

    @RabbitListener(queues = "${rabbitmq.queue.otp}")
    public void handleOtpEmail(OtpEmailEvent event) {
        log.info("[RabbitMQ] OTP email: type={}, to={}", event.getOtpType(), event.getToEmail());
        try {
            emailService.sendOtpEmail(
                    event.getToEmail(),
                    event.getToName(),
                    event.getOtpCode(),
                    OtpType.valueOf(event.getOtpType()),
                    event.getIpAddress(),
                    event.getLocation(),
                    event.getUserId()
            );
        } catch (Exception e) {
            log.error("Failed to send OTP email: {}", e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.welcome}")
    public void handleWelcomeEmail(WelcomeEmailEvent event) {
        log.info("[RabbitMQ] Welcome email: to={}", event.getToEmail());
        try {
            emailService.sendWelcomeEmail(
                    event.getToEmail(),
                    event.getToName(),
                    event.getPhone(),
                    event.getHasPassword(),
                    event.getHasGoogleLinked(),
                    event.getUserId()
            );
        } catch (Exception e) {
            log.error("Failed to send welcome email: {}", e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.alert}")
    public void handleAlertEmail(AlertEmailEvent event) {
        log.info("[RabbitMQ] Alert email: type={}, to={}", event.getAlertType(), event.getToEmail());
        try {
            emailService.sendAlertEmail(
                    event.getToEmail(),
                    event.getToName(),
                    event.getAlertType(),
                    event.getIpAddress(),
                    event.getLocation(),
                    event.getDeviceInfo(),
                    event.getUserId()
            );
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.inapp}")
    public void handleInAppNotification(InAppNotificationEvent event) {
        log.info("[RabbitMQ] InApp Notification: to={}", event.getRecipientId());
        try {
            inAppNotificationService.processNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process in-app notification: {}", e.getMessage());
            throw e;
        }
    }
}