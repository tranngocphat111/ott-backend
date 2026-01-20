package iuh.fit.ottbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enable2FARequest {
    @NotBlank(message = "OTP is required")
    private String otp;
}