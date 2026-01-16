package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Table(name = "official_accounts")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Setter
@Getter
public class OfficialAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

//    Chủ sở hữu
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;


    private String name;
    private String category;
    private String description;
    @Column(name = "website")
    private String websiteUrl;
    private String phone;
    private String email;
    private String address;

    @Column(name = "is_verified")
    private boolean isVerified;

    @Column(name = "followers_count")
    private int followersCount;

    @OneToMany(mappedBy = "oa", cascade = CascadeType.ALL)
    private Set<OAFollower> OAFollowers;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}

@Table(name = "oa_followers")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Setter
@Getter
class OAFollower {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oa_id", nullable = false)
    private OfficialAccount oa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "followed_at", nullable = false, updatable = false)
    private LocalDateTime followedAt;
}