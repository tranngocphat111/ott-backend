package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;
import mediaservice.models.enums.OfficialAccountStatusType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Table(name = "official_acounts")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("OFFICIAL_ACCOUNT")
public class OfficialAccount extends Account {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserAccount ownerUser;

    @Enumerated(EnumType.STRING)
    private OfficialAccountStatusType status;

    private boolean isVerified;

}
