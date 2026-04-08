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
public class RequestChangeEmailOtpRequest {
    @NotBlank(message = "NEW_EMAIL_IS_REQUIRED")
    private String newEmail;

    private String ipAddress;
}