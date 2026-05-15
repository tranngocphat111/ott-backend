package iuh.fit.userservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusSnapshot {
    private Boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime blockedUntil;
    private String blockedReason;
    private LocalDateTime deletedAt;
}
