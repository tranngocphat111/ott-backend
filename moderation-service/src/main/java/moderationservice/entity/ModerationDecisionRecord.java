package moderationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import moderationservice.enums.ContentType;
import moderationservice.enums.ModerationDecision;
import moderationservice.enums.ViolationSeverity;

import java.time.LocalDateTime;

@Entity
@Table(name = "moderation_decision_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationDecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "source_service", nullable = false)
    private String sourceService;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "content_ref_id", nullable = false)
    private String contentRefId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false)
    private ModerationDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private ViolationSeverity severity;

    @Column(name = "violation_type")
    private String violationType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "matched_labels", columnDefinition = "TEXT")
    private String matchedLabels;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}
