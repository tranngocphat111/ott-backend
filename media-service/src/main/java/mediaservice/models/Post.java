package mediaservice.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Post extends Content {

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "post")
    @JsonIgnore
    private Set<Tag> tags;
}
