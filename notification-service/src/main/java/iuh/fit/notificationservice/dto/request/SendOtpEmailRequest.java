package iuh.fit.notificationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String toEmail;

    private String toName;

    private String otpCode;

    @NotBlank(message = "OTP type is required")
    private String otpType;

    private String ipAddress;
    private String location;
    private String userId;
}