package iuh.fit.userservice.dto.response;

import java.time.LocalDateTime;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {
    private String phone;
    private String email;
    private LocalDateTime expiresAt;
    private String message;
}