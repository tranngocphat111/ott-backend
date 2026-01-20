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
public class ChangePhoneRequest {
    @NotBlank(message = "New phone is required")
    private String newPhone;

    @NotBlank(message = "OTP is required")
    private String otp;

    private String ipAddress;
}
