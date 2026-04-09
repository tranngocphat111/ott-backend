package iuh.fit.authservice.mapper;

import iuh.fit.authservice.dto.response.QrCodeResponse;
import iuh.fit.authservice.dto.response.QrStatusResponse;
import iuh.fit.authservice.entity.QrCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class QrCodeMapper {

    public QrCodeResponse toQrCodeResponse(QrCode qrCode) {
        long expirySeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), qrCode.getExpiresAt());
        return QrCodeResponse.builder()
                .qrId(qrCode.getId())
                .qrData(qrCode.getQrData())
                .status(qrCode.getStatus())
                .expiresAt(qrCode.getExpiresAt())
                .expirySeconds((int) Math.max(0, expirySeconds))
                .build();
    }

    public QrStatusResponse toQrStatusResponse(QrCode qrCode) {
        return QrStatusResponse.builder()
                .qrId(qrCode.getId())
                .status(qrCode.getStatus())
                .deviceInfo(qrCode.getScannedDeviceInfo())
                .ipAddress(qrCode.getScannedIpAddress())
                .location(qrCode.getLocation())
                .build();
    }
}