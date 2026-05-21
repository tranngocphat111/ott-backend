package iuh.fit.authservice.dto.request;

import iuh.fit.authservice.entity.enums.DeviceType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthWithTokenRequest {

    private String idToken;
    private String accessToken;

    private String deviceId;
    private DeviceType deviceType;
    private String deviceName;
    private String deviceInfo;
    private String ipAddress;
    private String location;
}
