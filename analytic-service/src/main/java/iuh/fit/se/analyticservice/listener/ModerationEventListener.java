package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.config.RabbitMqConfig;
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
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.USER_STATUS_CHANGED_QUEUE)
    public void handleUserStatusChangedEvent(UserStatusChangedEvent event) {
        try {
            validateEvent(event);
            String eventId = resolveEventId(event);
            if (adminAuditLogRepository.existsByEventId(eventId)) {
                log.warn("Duplicate event detected: eventId={}", eventId);
                return;
            }
            String actionType = resolveActionType(event);

            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .eventId(eventId)
                    .adminId(normalizeAdminId(resolveActorId(event)))
                    .targetUserId(event.getUserId())
                    .actionType(actionType)
                    .reason(event.getReason())
                    .durationMinutes(event.getDurationMinutes())
                    .oldValue(buildOldValue(event))
                    .newValue(buildNewValue(event))
                    .createdAt(resolveCreatedAt(event.getTimestamp()))
                    .build();

            adminAuditLogRepository.save(auditLog);

            log.info(
                    "Saved moderation audit log: eventId={}, adminId={}, targetUserId={}, actionType={}",
                    eventId,
                    auditLog.getAdminId(),
                    event.getUserId(),
                    auditLog.getActionType()
            );
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate moderation status event ignored: eventId={}",
                    event != null ? event.getEventId() : null);
        } catch (Exception ex) {
            log.error("Failed to process user.status.changed event: {}", event, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid moderation analytics event", ex);
        }
    }

    private void validateEvent(UserStatusChangedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event payload must not be null");
        }
        if (isBlank(event.getUserId())) {
            throw new IllegalArgumentException("userId is required");
        }
        if (isBlank(event.getActionType()) && isBlank(event.getNewStatus())) {
            throw new IllegalArgumentException("actionType is required");
        }
    }

    private String resolveEventId(UserStatusChangedEvent event) {
        if (!isBlank(event.getEventId())) {
            return event.getEventId().trim();
        }

        String source = String.join("|",
                normalizeNullable(event.getUserId()),
                normalizeNullable(event.getActionType()),
                normalizeNullable(event.getNewStatus()),
                normalizeNullable(resolveActorId(event)),
                String.valueOf(event.getTimestamp())
        );
        return "legacy-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveActorId(UserStatusChangedEvent event) {
        return !isBlank(event.getActorId()) ? event.getActorId() : event.getAdminId();
    }

    private String normalizeAdminId(String adminId) {
        return isBlank(adminId) ? "SYSTEM" : adminId.trim();
    }

    private LocalDateTime resolveCreatedAt(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
    }

    private String resolveActionType(UserStatusChangedEvent event) {
        if (!isBlank(event.getActionType())) {
            return event.getActionType().trim().toUpperCase(Locale.ROOT);
        }
        return mapActionType(event.getNewStatus());
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

    private String buildOldValue(UserStatusChangedEvent event) throws JsonProcessingException {
        if (event.getPreviousStatus() != null) {
            return objectMapper.writeValueAsString(event.getPreviousStatus());
        }

        Map<String, Object> legacyOldValue = new LinkedHashMap<>();
        legacyOldValue.put("status", normalizeNullable(event.getOldStatus()));
        return objectMapper.writeValueAsString(legacyOldValue);
    }

    private String buildNewValue(UserStatusChangedEvent event) throws JsonProcessingException {
        Map<String, Object> newValue = new LinkedHashMap<>();
        if (event.getNewStatusSnapshot() != null) {
            newValue.put("statusSnapshot", event.getNewStatusSnapshot());
        } else {
            newValue.put("status", normalizeNullable(event.getNewStatus()));
        }
        newValue.put("actionType", normalizeNullable(event.getActionType()));
        newValue.put("durationMinutes", event.getDurationMinutes());
        newValue.put("effectiveUntil", event.getEffectiveUntil());
        newValue.put("reason", normalizeNullable(event.getReason()));
        newValue.put("actorRole", normalizeNullable(event.getActorRole()));
        return objectMapper.writeValueAsString(newValue);
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
