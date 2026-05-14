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
public class LocalLoginRequest {

    // Chấp nhận cả phone hoặc email để đăng nhập
    @NotBlank(message = "IDENTIFIER_IS_REQUIRED")
    private String identifier;

    @NotBlank(message = "PASSWORD_IS_REQUIRED")
    private String password;

    private String otpCode;

    private String deviceId;
    private DeviceType deviceType;
    private String deviceName;
    private String deviceInfo;
    private String ipAddress;
    private String location;
}