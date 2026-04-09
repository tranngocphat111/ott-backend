package iuh.fit.authservice.dto.request;

import iuh.fit.authservice.entity.enums.DeviceType;
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
public class CompleteGoogleRegistrationRequest {

    @NotBlank(message = "TEMP_TOKEN_IS_REQUIRED")
    private String tempToken;

    @NotBlank(message = "PHONE_NUMBER_IS_REQUIRED")
    @Pattern(
            regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$",
            message = "INVALID_PHONE_FORMAT"
    )
    private String phone;

    private String deviceId;
    private DeviceType deviceType;
    private String deviceName;
    private String deviceInfo;
    private String ipAddress;
    private String location;
}