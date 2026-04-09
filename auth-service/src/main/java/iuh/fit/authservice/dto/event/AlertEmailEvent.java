package iuh.fit.authservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEmailEvent {
    private String userId;
    private String email;
    private String fullName;
    private String alertType;
    private String ipAddress;
    private String location;
    private String deviceInfo;
    private String timestamp;
}