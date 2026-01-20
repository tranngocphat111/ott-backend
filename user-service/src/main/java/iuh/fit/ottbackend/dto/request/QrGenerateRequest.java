package iuh.fit.ottbackend.dto.request;

import iuh.fit.ottbackend.entity.enums.DeviceType;
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
