package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Table(name = "users")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@DiscriminatorValue("USER")
public class UserAccount extends Account{
    private boolean isCreator;

    /** Nơi làm việc */
    private String work;

    /** Nơi ở hiện tại */
    private String location;

    /** Tình trạng quan hệ (e.g. Độc thân, Đã kết hôn, ...) */
    private String relationshipStatus;

    @ToString.Exclude
    @OneToOne(mappedBy = "user")
    private CreatorProfile creatorProfile;

    @OneToMany(mappedBy = "ownerUser", fetch = FetchType.LAZY)
    private Set<OfficialAccount> officialAccounts;


    @OneToMany(mappedBy = "requester", fetch = FetchType.LAZY)
    private Set<Relationship> requestedRelationship;

    @OneToMany(mappedBy = "receiver", fetch = FetchType.LAZY)
    private Set<Relationship> receivedRelationship;

    @ToString.Exclude
    @OneToMany(mappedBy = "follower", fetch = FetchType.LAZY)
    private Set<Follow> followings;

}
