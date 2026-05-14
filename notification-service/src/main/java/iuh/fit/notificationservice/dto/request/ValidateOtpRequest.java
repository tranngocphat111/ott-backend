package iuh.fit.notificationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateOtpRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String otpType;

    @NotBlank
    private String code;
}