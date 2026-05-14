package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import mediaservice.models.enums.CreatorProfileStatusType;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Table(name = "create_profiles")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreatorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    private CreatorProfileStatusType status;

    private boolean isVerified;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;
}
