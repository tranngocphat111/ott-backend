package iuh.fit.se.analyticservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

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
        name = "content_violation_logs",
        indexes = {
                @Index(name = "idx_content_violation_detected_at", columnList = "detected_at"),
                @Index(name = "idx_content_violation_user", columnList = "user_id"),
                @Index(name = "idx_content_violation_severity", columnList = "severity"),
                @Index(name = "idx_content_violation_type", columnList = "violation_type")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentViolationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "violation_id", nullable = false, unique = true, updatable = false)
    private String violationId;

    @Column(name = "source_service")
    private String sourceService;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "content_ref_id")
    private String contentRefId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "severity")
    private String severity;

    @Column(name = "violation_type")
    private String violationType;

    @Column(name = "matched_labels", columnDefinition = "TEXT")
    private String matchedLabels;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    public void prePersist() {
        if (loggedAt == null) {
            loggedAt = LocalDateTime.now();
        }
    }
}
