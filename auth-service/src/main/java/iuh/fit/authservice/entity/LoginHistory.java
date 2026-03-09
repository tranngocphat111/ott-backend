package iuh.fit.authservice.entity;

import iuh.fit.authservice.entity.enums.DeviceType;
import iuh.fit.authservice.entity.enums.LoginMethod;
import iuh.fit.authservice.entity.enums.LoginStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_history", indexes = {
        @Index(name = "idx_login_history_user", columnList = "user_id, created_at"),
        @Index(name = "idx_login_status", columnList = "status"),
        @Index(name = "idx_login_method", columnList = "login_method")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_method", length = 20, nullable = false)
    private LoginMethod loginMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoginStatus status;

    @Column(length = 255)
    private String location;

    @Column(name = "qr_code_id")
    private String qrCodeId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}