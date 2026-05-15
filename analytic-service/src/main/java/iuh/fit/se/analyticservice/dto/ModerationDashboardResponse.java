package iuh.fit.se.analyticservice.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationDashboardResponse {
    private long totalBannedUsers;
    private List<AuditLogDTO> recentLogs;
}
