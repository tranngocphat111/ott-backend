package iuh.fit.ottbackend.dto.request;

import lombok.*;

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