package iuh.fit.userservice.dto.request;

import iuh.fit.userservice.dto.enums.UserStatusAction;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserStatusRequest {

    @NotNull
    private UserStatusAction actionType;

    private String reason;

    private Long durationMinutes;

    private Boolean isPermanent;

    @AssertTrue(message = "durationMinutes must be null when isPermanent is true")
    public boolean isDurationValidForPermanent() {
        if (Boolean.TRUE.equals(isPermanent)) {
            return durationMinutes == null;
        }
        return true;
    }
}
