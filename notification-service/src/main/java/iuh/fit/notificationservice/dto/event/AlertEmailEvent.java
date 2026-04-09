package iuh.fit.notificationservice.dto.event;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEmailEvent {
    private String toEmail;
    private String toName;
    private String alertType;
    private String ipAddress;
    private String location;
    private String deviceInfo;
    private String userId;
}