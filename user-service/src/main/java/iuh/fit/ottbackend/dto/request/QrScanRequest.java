package iuh.fit.ottbackend.dto.request;

import iuh.fit.ottbackend.entity.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrScanRequest {

    private String qrData;

    private String deviceId;
    private DeviceType deviceType;
    private String deviceInfo;
    private String ipAddress;
    private String location;
}
