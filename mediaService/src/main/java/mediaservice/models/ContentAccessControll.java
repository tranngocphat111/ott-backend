package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Table(name = "content_access_controll")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Data
public class ContentAccessControll {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne
    private Content content;


//    true là danh sách những người bị cấm coi
//    false là danh sách những người cụ thể được coi
    @Column(name = "is_excluded")
    private boolean isExcluded;

    @OneToMany
    private Set<User> user;
}
