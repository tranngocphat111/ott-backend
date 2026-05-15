package iuh.fit.se.analyticservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverviewResponse {
    private long totalUsers;
    private long totalLogins;
    private long totalMessages;
    private long totalPosts;
    private long dau;
    private long mau;
    private Double userDelta;
    private Double loginDelta;
    private Double messageDelta;
    private Double postDelta;

    // convenience constructor for old callers that only provide totals
    public OverviewResponse(long totalUsers, long totalLogins, long totalMessages, long totalPosts) {
        this.totalUsers = totalUsers;
        this.totalLogins = totalLogins;
        this.totalMessages = totalMessages;
        this.totalPosts = totalPosts;
        this.dau = 0;
        this.mau = 0;
    }

    public OverviewResponse(long totalUsers, long totalLogins, long totalMessages, long totalPosts, long dau, long mau) {
        this.totalUsers = totalUsers;
        this.totalLogins = totalLogins;
        this.totalMessages = totalMessages;
        this.totalPosts = totalPosts;
        this.dau = dau;
        this.mau = mau;
    }
}
