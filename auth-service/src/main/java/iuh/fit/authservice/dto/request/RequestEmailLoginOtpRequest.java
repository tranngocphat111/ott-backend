package iuh.fit.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequestEmailLoginOtpRequest {

    @NotBlank(message = "EMAIL_IS_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    private String email;

    private String ipAddress;
    private String deviceInfo;
    private String location;
}