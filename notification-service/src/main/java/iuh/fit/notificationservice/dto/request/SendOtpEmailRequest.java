package iuh.fit.notificationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpEmailRequest {

    @NotBlank(message = "EMAIL_IS_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    private String toEmail;

    private String toName;

    private String otpCode;

    @NotBlank(message = "OTP_TYPE_IS_REQUIRED")
    private String otpType;

    private String ipAddress;
    private String location;
    private String userId;
}