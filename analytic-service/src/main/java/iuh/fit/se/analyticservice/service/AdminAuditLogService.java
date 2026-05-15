package iuh.fit.se.analyticservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.dto.AuditLogDTO;
import iuh.fit.se.analyticservice.dto.ModerationDashboardResponse;
import iuh.fit.se.analyticservice.dto.PaginatedAuditLogsResponse;
import iuh.fit.se.analyticservice.entity.AdminAuditLog;
import iuh.fit.se.analyticservice.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public void logAction(String adminId, String actionType, String targetUserId) {
        AdminAuditLog log = AdminAuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .createdAt(LocalDateTime.now())
                .build();
        adminAuditLogRepository.save(log);
    }

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

    public ModerationDashboardResponse getDashboardMetrics() {
        long totalBannedUsers = adminAuditLogRepository.countByActionType("BLOCK");
        List<AuditLogDTO> recentLogs = adminAuditLogRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::toAuditLogDTO)
                .toList();

        return new ModerationDashboardResponse(totalBannedUsers, recentLogs);
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
                log.getCreatedAt()
        );
    }
}
