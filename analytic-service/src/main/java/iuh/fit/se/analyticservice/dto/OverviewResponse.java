package iuh.fit.se.analyticservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverviewResponse {
    private long totalUsers;
    private long totalMessages;
    private long totalPosts;
}
