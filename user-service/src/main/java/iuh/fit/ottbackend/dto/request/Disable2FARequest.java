package iuh.fit.ottbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Disable2FARequest {
    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "OTP is required")
    private String otp;
}