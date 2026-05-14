package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import mediaservice.models.enums.FollowTargetType;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Table(name = "follows")



@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id")
    private UserAccount follower;

    @Enumerated(EnumType.STRING)
    private FollowTargetType targetType;

    private String targetId;


    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
