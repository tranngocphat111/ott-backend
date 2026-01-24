package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import mediaservice.models.enums.OfficialAccountStatusType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Table(name = "official_acounts")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class OfficialAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserAccount ownerUser;

    private String username;
    private String displayName;

    private String avatarUrl;
    private String coverUrl;

    private String bio;

    @Enumerated(EnumType.STRING)
    private OfficialAccountStatusType status;

    private boolean isVerified;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
