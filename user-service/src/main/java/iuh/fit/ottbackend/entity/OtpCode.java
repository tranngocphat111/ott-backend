package iuh.fit.ottbackend.entity;

import iuh.fit.ottbackend.entity.enums.OtpType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_codes", indexes = {
        @Index(name = "idx_otp_user", columnList = "user_id, type, is_used"),
        @Index(name = "idx_otp_phone", columnList = "phone, expires_at"),
        @Index(name = "idx_otp_email", columnList = "email, expires_at"),
        @Index(name = "idx_otp_type", columnList = "type, is_used")
})
@Check(constraints = "type IN ('REGISTER', 'EMAIL_VERIFICATION', 'LOGIN_OTP_EMAIL', 'TWO_FACTOR_AUTH', 'RESET_PASSWORD', 'CHANGE_PASSWORD', 'CHANGE_EMAIL', 'CHANGE_PHONE', 'LINK_GOOGLE_ACCOUNT', 'LINK_PHONE', 'LINK_EMAIL', 'DELETE_ACCOUNT', 'ENABLE_TWO_FACTOR', 'DISABLE_TWO_FACTOR')")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpType type;

    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "attempts")
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isUsed && !isExpired() && !isBlocked();
    }

    public boolean isBlocked() {
        return attempts >= maxAttempts;
    }

    public void incrementAttempts() {
        this.attempts++;
        if (isBlocked() && blockedAt == null) {
            this.blockedAt = LocalDateTime.now();
        }
    }
}