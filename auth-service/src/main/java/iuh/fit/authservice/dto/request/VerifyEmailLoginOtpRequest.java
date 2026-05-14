package iuh.fit.authservice.dto.request;

import iuh.fit.authservice.entity.enums.DeviceType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyEmailLoginOtpRequest {

    @NotBlank(message = "EMAIL_IS_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    private String email;

    @NotBlank(message = "OTP_CODE_IS_REQUIRED")
    @Pattern(
            regexp = "\\d{6}",
            message = "OTP_MUST_BE_6_DIGITS"
    )
    private String otpCode;

    @NotBlank(message = "DEVICE_ID_IS_REQUIRED")
    private String deviceId;

    private DeviceType deviceType;
    private String deviceName;
    private String ipAddress;
    private String deviceInfo;
}