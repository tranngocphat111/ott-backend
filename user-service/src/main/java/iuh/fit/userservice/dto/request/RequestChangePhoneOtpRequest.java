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
public class RequestChangePhoneOtpRequest {
    @NotBlank(message = "NEW_PHONE_IS_REQUIRED")
    private String newPhone;

    private String ipAddress;
}