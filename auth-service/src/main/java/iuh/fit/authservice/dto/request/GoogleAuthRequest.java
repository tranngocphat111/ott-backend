package iuh.fit.authservice.dto.request;

import iuh.fit.authservice.entity.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest {

    @NotBlank(message = "GOOGLE_AUTHORIZATION_CODE_IS_REQUIRED")
    private String code;

    private String redirectUri;

    private String deviceId;
    private DeviceType deviceType;
    private String deviceName;
    private String deviceInfo;
    private String ipAddress;
    private String location;
}