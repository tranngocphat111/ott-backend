package iuh.fit.authservice.dto.request;

import iuh.fit.authservice.entity.enums.DeviceType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrGenerateRequest {
    private String deviceId;
    private DeviceType deviceType;
    private String deviceInfo;
    private String ipAddress;
}