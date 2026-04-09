package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "OLD_PASSWORD_IS_REQUIRED")
    private String oldPassword;

    @NotBlank(message = "NEW_PASSWORD_IS_REQUIRED")
    private String newPassword;

    private String ipAddress;

    private String deviceInfo;
}