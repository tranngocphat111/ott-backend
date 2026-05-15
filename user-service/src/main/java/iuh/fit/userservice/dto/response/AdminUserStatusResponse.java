package iuh.fit.userservice.dto.response;

import iuh.fit.userservice.entity.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserStatusResponse {
    private String userId;
    private AccountType accountType;
    private Boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime blockedUntil;
    private String blockedReason;
    private LocalDateTime deletedAt;
    private LocalDateTime updatedAt;
}
