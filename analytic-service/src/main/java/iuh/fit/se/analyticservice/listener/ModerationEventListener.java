package iuh.fit.se.analyticservice.listener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.dto.UserStatusChangedEvent;
import iuh.fit.se.analyticservice.entity.AdminAuditLog;
import iuh.fit.se.analyticservice.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationEventListener {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "analytic.user.status.queue", durable = "true"),
                    exchange = @Exchange(value = "user.events", type = ExchangeTypes.TOPIC, durable = "true"),
                    key = "user.status.changed"
            )
    )
    public void handleUserStatusChangedEvent(UserStatusChangedEvent event) {
        try {
            validateEvent(event);
            if (adminAuditLogRepository.existsByEventId(event.getEventId())) {
                log.warn("Duplicate event detected: eventId={}", event.getEventId());
                return;
            }

            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .eventId(event.getEventId())
                    .adminId(normalizeAdminId(event.getAdminId()))
                    .targetUserId(event.getUserId())
                    .actionType(mapActionType(event.getNewStatus()))
                    .reason(event.getReason())
                    .durationMinutes(event.getDurationMinutes())
                    .createdAt(resolveCreatedAt(event.getTimestamp()))
                    .build();

            adminAuditLogRepository.save(auditLog);

            log.info(
                    "Saved moderation audit log: eventId={}, adminId={}, targetUserId={}, oldStatus={}, newStatus={}, actionType={}",
                    event.getEventId(),
                    auditLog.getAdminId(),
                    event.getUserId(),
                    event.getOldStatus(),
                    event.getNewStatus(),
                    auditLog.getActionType()
            );
        } catch (Exception ex) {
            log.error("Failed to process user.status.changed event: {}", event, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid moderation analytics event", ex);
        }
    }

    private void validateEvent(UserStatusChangedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event payload must not be null");
        }
        if (isBlank(event.getEventId())) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (isBlank(event.getUserId())) {
            throw new IllegalArgumentException("userId is required");
        }
        if (isBlank(event.getNewStatus())) {
            throw new IllegalArgumentException("newStatus is required");
        }
    }

    private String normalizeAdminId(String adminId) {
        return isBlank(adminId) ? "SYSTEM" : adminId.trim();
    }

    private LocalDateTime resolveCreatedAt(java.time.Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
    }

    private String mapActionType(String newStatus) {
        String normalizedStatus = newStatus.trim().toUpperCase(Locale.ROOT);

        return switch (normalizedStatus) {
            case "BANNED", "BLOCKED", "SUSPENDED" -> "BLOCK";
            case "ACTIVE", "UNBANNED", "UNBLOCKED" -> "UNBLOCK";
            case "SOFT_DELETED", "DELETED" -> "SOFT_DELETE";
            case "RESTORED" -> "RESTORE";
            default -> normalizedStatus;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
