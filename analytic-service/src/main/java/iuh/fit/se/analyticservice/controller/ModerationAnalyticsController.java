package iuh.fit.se.analyticservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import iuh.fit.se.analyticservice.dto.ModerationDashboardResponse;
import iuh.fit.se.analyticservice.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class ModerationAnalyticsController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping("/moderation/dashboard")
    public ModerationDashboardResponse getModerationDashboard() {
        return adminAuditLogService.getDashboardMetrics();
    }
}
