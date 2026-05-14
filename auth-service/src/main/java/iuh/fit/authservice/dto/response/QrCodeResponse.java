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
public class QrCodeResponse {
    private String qrId;
    private String qrData;
    private QrCodeStatus status;
    private LocalDateTime expiresAt;
    private Integer expirySeconds;
}