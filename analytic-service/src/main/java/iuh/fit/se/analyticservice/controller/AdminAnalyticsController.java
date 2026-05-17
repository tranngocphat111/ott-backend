package iuh.fit.se.analyticservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import iuh.fit.se.analyticservice.dto.DailyActivityResponse;
import iuh.fit.se.analyticservice.dto.DailyPostCountResponse;
import iuh.fit.se.analyticservice.dto.DailyUserTrendResponse;
import iuh.fit.se.analyticservice.dto.LoginMethodCountResponse;
import iuh.fit.se.analyticservice.dto.MessageTypesResponse;
import iuh.fit.se.analyticservice.dto.PaginatedAuditLogsResponse;
import iuh.fit.se.analyticservice.dto.PaginatedRecentUsersResponse;
import iuh.fit.se.analyticservice.dto.OverviewResponse;
import iuh.fit.se.analyticservice.service.AdminAnalyticsService;
import iuh.fit.se.analyticservice.service.AdminAuditLogService;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminAuditLogService adminAuditLogService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService, AdminAuditLogService adminAuditLogService) {
        this.adminAnalyticsService = adminAnalyticsService;
        this.adminAuditLogService = adminAuditLogService;
    }

    // 1) Overview tab
    @GetMapping("/overview")
    public OverviewResponse getOverview(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getOverview(timeRange);
    }

    // 2) Users tab
    @GetMapping("/users/recent")
    public PaginatedRecentUsersResponse getRecentUsers(
            @RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange,
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return adminAnalyticsService.getRecentUsers(timeRange, query, page, size);
    }

    // 2.1) User trend
    @GetMapping("/users/daily-trend")
    public List<DailyUserTrendResponse> getUserDailyTrend(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getUserDailyTrend(timeRange);
    }

    // 3) Messaging tab
    @GetMapping("/messages/types")
    public MessageTypesResponse getMessageTypes(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getMessageTypes(timeRange);
    }

    // 3.1) Login methods
    @GetMapping("/logins/methods")
    public List<LoginMethodCountResponse> getLoginMethods(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getLoginMethods(timeRange);
    }

    // 4) Social tab
    @GetMapping("/social/activity/daily")
    public List<DailyActivityResponse> getActivityDaily(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getDailyActivity(timeRange);
    }

    @GetMapping("/social/posts/daily")
    public List<DailyPostCountResponse> getPostsDaily(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getPostDailyOnly(timeRange);
    }

    // 5) Audit logs
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public PaginatedAuditLogsResponse getAuditLogs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return adminAuditLogService.getLogs(page, size);
    }
}
