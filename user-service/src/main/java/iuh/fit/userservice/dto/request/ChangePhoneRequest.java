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
public class ChangePhoneRequest {

    @NotBlank(message = "NEW_PHONE_IS_REQUIRED")
    private String newPhone;

    @NotBlank(message = "OTP_IS_REQUIRED")
    private String otp;

    private String ipAddress;
}