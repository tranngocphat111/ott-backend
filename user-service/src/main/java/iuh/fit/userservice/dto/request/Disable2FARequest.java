package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Disable2FARequest {

    @NotBlank(message = "PASSWORD_IS_REQUIRED")
    private String password;

    @NotBlank(message = "OTP_IS_REQUIRED")
    private String otp;
}