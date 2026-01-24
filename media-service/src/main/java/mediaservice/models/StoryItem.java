package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.StoryItemType;

@Table(name = "story_items")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class StoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private boolean isPrimary;

    private int zIndex;

    private double positionX;

    private double positionY;

    private double rotation;

    private double scale;

    private Long startTime;

    private Long endTime;
}
