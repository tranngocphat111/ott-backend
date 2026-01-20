package iuh.fit.ottbackend.dto.request;

import lombok.*;

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