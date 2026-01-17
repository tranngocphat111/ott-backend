package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Table(name = "stories")
@DiscriminatorValue("STORY")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Story extends Content{

    @Column(name = "expire_at")
    private LocalDateTime expireAt;
}
