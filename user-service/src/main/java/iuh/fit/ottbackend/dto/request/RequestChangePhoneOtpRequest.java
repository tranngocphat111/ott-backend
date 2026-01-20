package iuh.fit.ottbackend.dto.request;

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
    @NotBlank(message = "New phone is required")
    private String newPhone;

    private String ipAddress;
}
