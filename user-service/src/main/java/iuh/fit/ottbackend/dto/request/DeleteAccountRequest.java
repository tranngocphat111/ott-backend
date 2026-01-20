package iuh.fit.ottbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {
    @NotBlank(message = "OTP is required")
    private String otp;

    private String password;
    private String ipAddress;
}