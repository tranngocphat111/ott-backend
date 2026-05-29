package iuh.fit.se.analyticservice.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private String id;
    private String eventId;
    private String adminId;
    private String actionType;
    private String targetUserId;
    private String reason;
    private Long durationMinutes;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;

    public AuditLogDTO(
            String id,
            String eventId,
            String adminId,
            String actionType,
            String targetUserId,
            String reason,
            Long durationMinutes,
            LocalDateTime createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.adminId = adminId;
        this.actionType = actionType;
        this.targetUserId = targetUserId;
        this.reason = reason;
        this.durationMinutes = durationMinutes;
        this.createdAt = createdAt;
    }
}
