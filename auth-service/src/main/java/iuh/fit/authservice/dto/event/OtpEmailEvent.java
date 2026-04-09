package iuh.fit.authservice.dto.event;

import iuh.fit.authservice.entity.enums.OtpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpEmailEvent {
    private String email;
    private String fullName;
    private String otpCode;
    private OtpType otpType;
    private String ipAddress;
    private String location;
}