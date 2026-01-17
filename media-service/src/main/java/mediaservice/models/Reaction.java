package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;

@Table(name = "reactions")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Data
public class Reaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Reaction có thể thuộc về Content HOẶC Comment (nullable vì chỉ một trong hai)
    @ManyToOne
    @JoinColumn(name = "content_id", nullable = true)
    private Content content;

    @ManyToOne
    @JoinColumn(name = "comment_id", nullable = true)
    private Comment comment;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reaction_type", nullable = false)
    private String reactionType;
}
