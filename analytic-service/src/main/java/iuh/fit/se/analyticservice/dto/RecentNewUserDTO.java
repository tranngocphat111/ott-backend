package iuh.fit.se.analyticservice.dto;

import java.time.Instant;
import java.time.LocalDateTime;

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
    private Boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime blockedUntil;
    private String blockedReason;
    private LocalDateTime deletedAt;

    public RecentNewUserDTO(
            String userId,
            String email,
            String fullName,
            Instant registeredAt,
            boolean profileSynced) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.registeredAt = registeredAt;
        this.profileSynced = profileSynced;
    }
}
