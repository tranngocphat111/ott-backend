package iuh.fit.se.analyticservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "admin_audit_logs",
        indexes = {
                @Index(name = "idx_admin_audit_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_admin_audit_logs_action_type", columnList = "action_type"),
                @Index(name = "idx_admin_audit_logs_target_user", columnList = "target_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(name = "event_id", unique = true)
    private String eventId;

    @Column(name = "admin_id", nullable = false)
    private String adminId;

    @Column(name = "target_user_id", nullable = false)
    private String targetUserId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "duration_minutes")
    private Long durationMinutes;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
