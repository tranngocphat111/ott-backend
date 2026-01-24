package mediaservice.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Table(name = "user_accounts")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String username;
    private String displayName;

    private String email;

    private String avatarUrl;
    private String coverUrl;

    private String bio;

    private boolean isCreator;

    @ToString.Exclude
    @OneToOne(mappedBy = "user")
    private CreatorProfile creatorProfile;

    @ToString.Exclude
    @OneToMany(mappedBy = "follower", fetch = FetchType.LAZY)
    private Set<Follow> followings;

    @OneToMany(mappedBy = "ownerUser", fetch = FetchType.LAZY)
    private Set<OfficialAccount> officialAccounts;

    @OneToMany(mappedBy = "requester", fetch = FetchType.LAZY)
    private Set<Relationship> requestedRelationship;

    @OneToMany(mappedBy = "receiver", fetch = FetchType.LAZY)
    private Set<Relationship> receivedRelationship;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;



}
