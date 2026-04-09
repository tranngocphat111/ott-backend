package iuh.fit.authservice.dto.request;

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
public class Request2FAOtpRequest {

    @NotBlank(message = "PHONE_NUMBER_IS_REQUIRED")
    @Pattern(
            regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$",
            message = "INVALID_PHONE_FORMAT"
    )
    private String phone;

    private String ipAddress;
    private String location;
}