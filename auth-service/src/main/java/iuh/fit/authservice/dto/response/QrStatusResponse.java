package iuh.fit.authservice.dto.response;

import iuh.fit.authservice.entity.enums.QrCodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrStatusResponse {
    private String qrId;
    private QrCodeStatus status;
    private String message;

    private String deviceInfo;
    private String ipAddress;
    private String location;

    private String sessionToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
}
