package iuh.fit.authservice.dto.response;

import iuh.fit.authservice.entity.enums.DeviceType;
import iuh.fit.authservice.entity.enums.LoginMethod;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private String id;
    private String deviceId;
    private DeviceType deviceType;
    private String deviceName;
    private String ipAddress;
    private String location;
    private LoginMethod loginMethod;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime expiresAt;
    private boolean isActive;
    private boolean isCurrent;
    private boolean twoFactorVerified;
}