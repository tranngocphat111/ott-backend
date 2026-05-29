package iuh.fit.se.analyticservice.listener;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.AdminAuditEvent;
import iuh.fit.se.analyticservice.entity.AdminAuditLog;
import iuh.fit.se.analyticservice.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditEventListener {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @RabbitListener(queues = RabbitMqConfig.ADMIN_AUDIT_QUEUE)
    public void handleAdminAuditEvent(AdminAuditEvent event) {
        try {
            validateEvent(event);
            if (adminAuditLogRepository.existsByEventId(event.getEventId())) {
                log.warn("Duplicate admin audit event ignored: eventId={}", event.getEventId());
                return;
            }

            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .eventId(event.getEventId())
                    .adminId(normalizeActor(event.getActorId()))
                    .actionType(event.getActionType().trim().toUpperCase())
                    .targetUserId(event.getTargetId())
                    .reason(event.getReason())
                    .oldValue(event.getOldValue())
                    .newValue(event.getNewValue())
                    .createdAt(event.getTimestamp() == null
                            ? null
                            : LocalDateTime.ofInstant(event.getTimestamp(), ZoneId.systemDefault()))
                    .build();

            adminAuditLogRepository.save(auditLog);
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate admin audit event ignored: eventId={}",
                    event != null ? event.getEventId() : null);
        } catch (Exception ex) {
            log.error("Failed to process admin audit event: {}", event, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid admin audit event", ex);
        }
    }

    private void validateEvent(AdminAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event payload must not be null");
        }
        if (isBlank(event.getEventId())) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (isBlank(event.getActionType())) {
            throw new IllegalArgumentException("actionType is required");
        }
    }

    private String normalizeActor(String actorId) {
        return isBlank(actorId) ? "ADMIN" : actorId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
