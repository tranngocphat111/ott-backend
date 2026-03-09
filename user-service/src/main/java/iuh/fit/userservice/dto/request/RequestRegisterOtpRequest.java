package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestRegisterOtpRequest {
    @NotBlank
    private String phone;
    @NotBlank
    private String email;
    @NotBlank
    private String fullName;
    private String ipAddress;
    private String location;
}