package iuh.fit.userservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLogoutEvent {
    private String userId;
    private String sessionId; // Optional, can be null
    private String deviceId; // Optional, can be null
    private java.util.List<String> revokedDeviceIds; // For OTHERS action
    private String action; // e.g. "ALL", "SPECIFIC", "OTHERS"
}
