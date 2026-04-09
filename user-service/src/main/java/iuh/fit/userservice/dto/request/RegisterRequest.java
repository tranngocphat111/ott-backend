package iuh.fit.userservice.dto.request;

import iuh.fit.userservice.entity.enums.DeviceType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "PHONE_NUMBER_IS_REQUIRED")
    @Pattern(
            regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$",
            message = "INVALID_PHONE_FORMAT"
    )
    private String phone;

    @NotBlank(message = "EMAIL_IS_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    private String email;

    @NotBlank(message = "PASSWORD_IS_REQUIRED")
    private String password;

    @NotBlank(message = "FULL_NAME_IS_REQUIRED")
    private String fullName;

    @NotBlank(message = "OTP_IS_REQUIRED")
    @Pattern(
            regexp = "^\\d{6}$",
            message = "OTP_MUST_BE_6_DIGITS"
    )
    private String otp;

    private String deviceId;
    private DeviceType deviceType;
    private String deviceName;
    private String deviceInfo;
    private String ipAddress;
    private String location;
}