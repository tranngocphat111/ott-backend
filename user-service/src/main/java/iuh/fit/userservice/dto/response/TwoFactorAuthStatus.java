package iuh.fit.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAuthStatus {
    private Boolean enabled;
    private LocalDateTime enabledAt;
    private LocalDateTime lastUsedAt;
    private int remainingBackupCodes;
}
