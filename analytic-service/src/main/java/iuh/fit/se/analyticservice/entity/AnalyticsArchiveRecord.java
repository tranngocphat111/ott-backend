package iuh.fit.se.analyticservice.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import iuh.fit.se.analyticservice.enums.ArchiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "analytics_archive_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_analytics_archive_event_date",
                        columnNames = {"event_type", "archive_date"}
                )
        },
        indexes = {
                @Index(name = "idx_analytics_archive_status", columnList = "status"),
                @Index(name = "idx_analytics_archive_event_date", columnList = "event_type,archive_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsArchiveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "archive_date", nullable = false)
    private LocalDate archiveDate;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 1024)
    private String s3Key;

    @Builder.Default
    @Column(name = "row_count", nullable = false)
    private long rowCount = 0;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ArchiveStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "archived_at")
    private Instant archivedAt;

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
        if (status == null) {
            status = ArchiveStatus.IN_PROGRESS;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
