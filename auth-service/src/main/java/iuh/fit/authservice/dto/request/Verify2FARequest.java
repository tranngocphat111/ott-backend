package iuh.fit.authservice.dto.request;

import iuh.fit.authservice.entity.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Verify2FARequest {
    @NotBlank(message = "Temp token is required")
    private String tempToken;

    @NotBlank(message = "OTP is required")
    private String otpCode;

    private String deviceId;
    private DeviceType deviceType;
    private String ipAddress;
    private String deviceInfo;
}