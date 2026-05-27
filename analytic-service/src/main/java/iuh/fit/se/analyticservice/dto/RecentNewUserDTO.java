package iuh.fit.se.analyticservice.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentNewUserDTO {
    private String userId;
    private String email;
    private String fullName;
    private Instant registeredAt;
    private boolean profileSynced;
}
