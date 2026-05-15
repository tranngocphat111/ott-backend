package iuh.fit.userservice.dto.event;

import iuh.fit.userservice.dto.enums.UserStatusAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusChangedEvent {
    private String userId;
    private UserStatusAction actionType;
    private String actorId;
    private String actorRole;
    private UserStatusSnapshot previousStatus;
    private UserStatusSnapshot newStatus;
    private String reason;
    private LocalDateTime effectiveUntil;
    private LocalDateTime timestamp;
}
