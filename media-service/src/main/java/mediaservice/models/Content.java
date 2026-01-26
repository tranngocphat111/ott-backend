package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.ContentTargetType;
import mediaservice.models.enums.VisibilityType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Table(name = "contents")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "content_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;


    @Enumerated(EnumType.STRING)
    private ContentStatusType status;

    @Enumerated(EnumType.STRING)
    private VisibilityType visibility;

    @OneToMany(mappedBy = "content")
    private Set<ContentAccessControl> accessControls;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToMany
    @JoinTable(
            name = "content_hashtag",
            joinColumns = @JoinColumn(name = "content_id"),
            inverseJoinColumns = @JoinColumn(name = "hashtag_id")
    )
    private Set<HashTag> hashTags;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
