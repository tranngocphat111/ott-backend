package iuh.fit.notificationservice.dto.event;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpEmailEvent {
    private String toEmail;
    private String toName;
    private String otpCode;
    private String otpType;
    private String ipAddress;
    private String location;
    private String userId;
}