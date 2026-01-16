package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Table(name = "posts")
@DiscriminatorValue("POST")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Post extends Content {

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "post")
    private Set<Tag> tags;
}
