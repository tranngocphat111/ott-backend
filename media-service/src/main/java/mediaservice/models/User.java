package mediaservice.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Table(name = "users")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Setter
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(unique = true)
    private String username;
    @Column(name = "avatar_url")
    private String avatarUrl;


//    user này sở hữu bao nhiêu official account
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    private Set<OfficialAccount> officialAccounts;
}
