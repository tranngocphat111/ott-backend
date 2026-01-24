package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.MentionTargetType;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Table(name = "mentions")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Mention {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    private MentionTargetType targetType;
    private String targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tagged_user_id")
    private UserAccount taggedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tagged_by_user_id")
    private UserAccount taggedByUser;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
