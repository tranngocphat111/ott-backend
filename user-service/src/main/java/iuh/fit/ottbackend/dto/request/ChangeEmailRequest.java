package iuh.fit.ottbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEmailRequest {
    @NotBlank(message = "New email is required")
    private String newEmail;

    @NotBlank(message = "OTP is required")
    private String otp;

    private String ipAddress;
}
