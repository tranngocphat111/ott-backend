package iuh.fit.se.analyticservice.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private long totalLogins;
    private long totalRegistrations;
    private List<RecentNewUserDTO> recentNewUsers;
}
