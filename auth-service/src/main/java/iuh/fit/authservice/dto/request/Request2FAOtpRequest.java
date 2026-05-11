package iuh.fit.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Request2FAOtpRequest {

    // Chấp nhận cả phone hoặc email để gửi lại OTP 2FA
    @NotBlank(message = "IDENTIFIER_IS_REQUIRED")
    private String identifier;

    private String ipAddress;
    private String location;
}