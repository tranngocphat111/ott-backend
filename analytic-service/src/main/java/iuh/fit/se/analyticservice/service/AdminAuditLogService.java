package iuh.fit.se.analyticservice.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.se.analyticservice.dto.AdminAuditEvent;
import iuh.fit.se.analyticservice.dto.AuditLogDTO;
import iuh.fit.se.analyticservice.dto.ContentViolationLogDTO;
import iuh.fit.se.analyticservice.dto.ModerationDashboardResponse;
import iuh.fit.se.analyticservice.dto.PaginatedAuditLogsResponse;
import iuh.fit.se.analyticservice.dto.UserStatusChangedEvent;
import iuh.fit.se.analyticservice.entity.AdminAuditLog;
import iuh.fit.se.analyticservice.entity.ContentViolationLog;
import iuh.fit.se.analyticservice.repository.AdminAuditLogRepository;
import iuh.fit.se.analyticservice.repository.ContentViolationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ContentViolationLogRepository contentViolationLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @CacheEvict(cacheNames = {"adminAuditLogs", "moderationDashboard"}, allEntries = true)
    public void logAction(String adminId, String actionType, String targetUserId) {
        AdminAuditLog logAction = AdminAuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .createdAt(LocalDateTime.now())
                .build();
        adminAuditLogRepository.save(logAction);
    }

    @Transactional
    @CacheEvict(cacheNames = {"adminAuditLogs", "moderationDashboard"}, allEntries = true)
    public void recordAdminAuditEvent(AdminAuditEvent event) {
        validateAdminAuditEvent(event);
        if (adminAuditLogRepository.existsByEventId(event.getEventId())) {
            log.warn("Duplicate admin audit event ignored: eventId={}", event.getEventId());
            return;
        }

        AdminAuditLog auditLog = AdminAuditLog.builder()
                .eventId(event.getEventId())
                .adminId(normalizeActor(event.getActorId()))
                .actionType(event.getActionType().trim().toUpperCase(Locale.ROOT))
                .targetUserId(event.getTargetId())
                .reason(event.getReason())
                .oldValue(event.getOldValue())
                .newValue(event.getNewValue())
                .createdAt(toLocalDateTime(event.getTimestamp()))
                .build();

        saveIgnoringDuplicate(auditLog, event.getEventId(), "admin audit");
    }

    @Transactional
    @CacheEvict(cacheNames = {"adminAuditLogs", "moderationDashboard"}, allEntries = true)
    public void recordUserStatusChanged(UserStatusChangedEvent event) throws JsonProcessingException {
        validateUserStatusEvent(event);
        String eventId = resolveEventId(event);
        if (adminAuditLogRepository.existsByEventId(eventId)) {
            log.warn("Duplicate user status event ignored: eventId={}", eventId);
            return;
        }

        String actionType = resolveActionType(event);
        AdminAuditLog auditLog = AdminAuditLog.builder()
                .eventId(eventId)
                .adminId(normalizeActor(resolveActorId(event)))
                .targetUserId(event.getUserId())
                .actionType(actionType)
                .reason(event.getReason())
                .durationMinutes(event.getDurationMinutes())
                .oldValue(buildOldValue(event))
                .newValue(buildNewValue(event))
                .createdAt(toLocalDateTime(event.getTimestamp()))
                .build();

        saveIgnoringDuplicate(auditLog, eventId, "user status");
        log.info(
                "Saved moderation audit log: eventId={}, adminId={}, targetUserId={}, actionType={}",
                eventId,
                auditLog.getAdminId(),
                event.getUserId(),
                auditLog.getActionType()
        );
    }

    @Cacheable(cacheNames = "adminAuditLogs", key = "{#page, #size}", condition = "@environment.getProperty('analytics.cache.enabled', 'true') == 'true'")
    public PaginatedAuditLogsResponse getLogs(int page, int size) {
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        Page<AdminAuditLog> result = adminAuditLogRepository.findAll(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<AuditLogDTO> items = result.getContent().stream()
                .map(this::toAuditLogDTO)
                .toList();

        return new PaginatedAuditLogsResponse(
                items,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages()
        );
    }

    @Cacheable(cacheNames = "moderationDashboard", key = "'metrics'", condition = "@environment.getProperty('analytics.cache.enabled', 'true') == 'true'")
    public ModerationDashboardResponse getDashboardMetrics() {
        long totalBannedUsers = adminAuditLogRepository.countByActionType("USER_BLOCK")
                + adminAuditLogRepository.countByActionType("BLOCK");
        List<AuditLogDTO> recentLogs = adminAuditLogRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::toAuditLogDTO)
                .toList();
        long totalContentViolations = contentViolationLogRepository.count();
        List<ContentViolationLogDTO> recentContentViolations = contentViolationLogRepository
                .findTop10ByOrderByDetectedAtDesc()
                .stream()
                .map(this::toContentViolationLogDTO)
                .toList();

        return new ModerationDashboardResponse(
                totalBannedUsers,
                recentLogs,
                totalContentViolations,
                recentContentViolations
        );
    }

    private AuditLogDTO toAuditLogDTO(AdminAuditLog log) {
        return new AuditLogDTO(
                log.getId(),
                log.getEventId(),
                log.getAdminId(),
                log.getActionType(),
                log.getTargetUserId(),
                log.getReason(),
                log.getDurationMinutes(),
                log.getOldValue(),
                log.getNewValue(),
                log.getCreatedAt()
        );
    }

    private ContentViolationLogDTO toContentViolationLogDTO(ContentViolationLog log) {
        return new ContentViolationLogDTO(
                log.getId(),
                log.getViolationId(),
                log.getSourceService(),
                log.getContentType(),
                log.getContentRefId(),
                log.getUserId(),
                log.getSeverity(),
                log.getViolationType(),
                log.getMatchedLabels(),
                log.getDetectedAt(),
                log.getLoggedAt()
        );
    }

    private void validateAdminAuditEvent(AdminAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event payload must not be null");
        }
        if (isBlank(event.getEventId())) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (isBlank(event.getActionType())) {
            throw new IllegalArgumentException("actionType is required");
        }
        if (isBlank(event.getTargetId())) {
            throw new IllegalArgumentException("targetId is required");
        }
    }

    private void validateUserStatusEvent(UserStatusChangedEvent event) {
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

    private void saveIgnoringDuplicate(AdminAuditLog auditLog, String eventId, String eventName) {
        try {
            adminAuditLogRepository.save(auditLog);
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate {} event ignored: eventId={}", eventName, eventId);
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

    private String normalizeActor(String actorId) {
        return isBlank(actorId) ? "SYSTEM" : actorId.trim();
    }

    private LocalDateTime toLocalDateTime(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
    }

    private String resolveActionType(UserStatusChangedEvent event) {
        String actionType;
        if (!isBlank(event.getActionType())) {
            actionType = event.getActionType().trim().toUpperCase(Locale.ROOT);
        } else {
            actionType = mapActionType(event.getNewStatus());
        }

        return switch (actionType) {
            case "BLOCK" -> "USER_BLOCK";
            case "UNBLOCK" -> "USER_UNBLOCK";
            case "DEACTIVATE", "SOFT_DELETE" -> "USER_DEACTIVATE";
            case "RESTORE" -> "USER_RESTORE";
            default -> actionType;
        };
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