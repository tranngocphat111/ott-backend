package iuh.fit.authservice.entity;

import iuh.fit.authservice.entity.enums.DeviceType;
import iuh.fit.authservice.entity.enums.QrCodeStatus;
import iuh.fit.authservice.entity.enums.QrCodeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "qr_codes", indexes = {
        @Index(name = "idx_qr_user", columnList = "user_id"),
        @Index(name = "idx_qr_status", columnList = "status, expires_at"),
        @Index(name = "idx_qr_data", columnList = "qr_data")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrCode {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "qr_type", nullable = false, length = 20)
    @Builder.Default
    private QrCodeType qrType = QrCodeType.LOGIN;

    @Column(name = "qr_data", nullable = false, unique = true, columnDefinition = "TEXT")
    private String qrData;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "scanned_device_id", length = 255)
    private String scannedDeviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scanned_device_type", length = 20)
    private DeviceType scannedDeviceType;

    @Column(name = "scanned_device_info", columnDefinition = "TEXT")
    private String scannedDeviceInfo;

    @Column(name = "scanned_ip_address", length = 45)
    private String scannedIpAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QrCodeStatus status = QrCodeStatus.PENDING;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0;
}