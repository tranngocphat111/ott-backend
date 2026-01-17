package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;

@Table(name = "tags")



@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Data
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "name")
    private String name;

    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;
}


