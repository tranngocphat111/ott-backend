package iuh.fit.se.analyticservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import iuh.fit.se.analyticservice.dto.DailyActivityResponse;
import iuh.fit.se.analyticservice.dto.DailyPostCountResponse;
import iuh.fit.se.analyticservice.dto.MessageTypesResponse;
import iuh.fit.se.analyticservice.dto.OverviewResponse;
import iuh.fit.se.analyticservice.dto.RecentNewUserDTO;
import iuh.fit.se.analyticservice.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    // 1) Overview tab
    @GetMapping("/overview")
    public OverviewResponse getOverview(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getOverview(timeRange);
    }

    // 2) Users tab
    @GetMapping("/users/recent")
    public List<RecentNewUserDTO> getRecentUsers(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getRecentUsers(timeRange);
    }

    // 3) Messaging tab
    @GetMapping("/messages/types")
    public MessageTypesResponse getMessageTypes(@RequestParam(name = "timeRange", defaultValue = "allTime") String timeRange) {
        return adminAnalyticsService.getMessageTypes(timeRange);
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
}
