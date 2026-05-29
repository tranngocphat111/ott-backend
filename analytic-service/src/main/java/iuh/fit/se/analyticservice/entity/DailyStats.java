package iuh.fit.se.analyticservice.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "daily_stats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_stats_stat_date", columnNames = "stat_date")
        },
        indexes = {
                @Index(name = "idx_daily_stats_stat_date", columnList = "stat_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Builder.Default
    @Column(name = "registered_users", nullable = false)
    private long registeredUsers = 0;

    @Builder.Default
    @Column(name = "login_events", nullable = false)
    private long loginEvents = 0;

    @Builder.Default
    @Column(name = "active_users", nullable = false)
    private long activeUsers = 0;

    @Builder.Default
    @Column(name = "message_events", nullable = false)
    private long messageEvents = 0;

    @Builder.Default
    @Column(name = "post_events", nullable = false)
    private long postEvents = 0;

    @Builder.Default
    @Column(name = "violation_events", nullable = false)
    private long violationEvents = 0;

    @Column(name = "aggregated_at", nullable = false)
    private Instant aggregatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (aggregatedAt == null) {
            aggregatedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        Instant now = Instant.now();
        aggregatedAt = now;
        updatedAt = now;
    }
}
