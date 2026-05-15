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
    private LocalDateTime createdAt;
}
