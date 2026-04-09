package iuh.fit.userservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPasswordResetRequest {
    private String phone;
    private String email;
    private String otp;
    private String newPassword;
    private String confirmPassword;
    private String ipAddress;

    public boolean isValid() {
        return (phone != null && !phone.isEmpty()) || (email != null && !email.isEmpty());
    }
}