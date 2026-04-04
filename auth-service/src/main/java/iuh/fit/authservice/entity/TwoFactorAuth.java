package iuh.fit.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "two_factor_auth", indexes = {
        @Index(name = "idx_2fa_user", columnList = "user_id"),
        @Index(name = "idx_2fa_enabled", columnList = "is_enabled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorAuth {
    @Id
    @Column(name = "user_id")
    private String userId;


    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = false;

    @Column(name = "secret_key", length = 255)
    private String secretKey;

    @Column(name = "backup_codes", columnDefinition = "TEXT[]")
    private String[] backupCodes;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "backup_codes_used")
    @Builder.Default
    private Integer backupCodesUsed = 0;

    @Column(name = "total_backup_codes")
    @Builder.Default
    private Integer totalBackupCodes = 10;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public int getRemainingBackupCodes() {
        return totalBackupCodes - backupCodesUsed;
    }

    public void enable() {
        this.isEnabled = true;
        this.enabledAt = LocalDateTime.now();
        this.disabledAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.isEnabled = false;
        this.disabledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}