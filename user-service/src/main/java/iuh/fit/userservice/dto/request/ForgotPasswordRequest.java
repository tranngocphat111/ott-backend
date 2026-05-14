package iuh.fit.userservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {
    private String phone;
    private String email;

    private String ipAddress;

    public boolean isValid() {
        return (phone != null && !phone.isEmpty()) || (email != null && !email.isEmpty());
    }
}