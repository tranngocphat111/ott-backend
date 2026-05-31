package mediaservice.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import mediaservice.models.enums.MediaModerationStatus;
import mediaservice.models.enums.MediaType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

@Table(name = "medias")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String url;
    private String caption;
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    private MediaModerationStatus moderationStatus = MediaModerationStatus.CLEAN;

    private String moderationViolationId;
    private String moderationSeverity;
    private String moderationViolationType;

    @Column(columnDefinition = "TEXT")
    private String moderationMatchedLabels;

    @Column(columnDefinition = "TEXT")
    private String moderationReason;

    private Instant moderationDetectedAt;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "media")
    private Set<MediaMusic> mediaMusics;
}
