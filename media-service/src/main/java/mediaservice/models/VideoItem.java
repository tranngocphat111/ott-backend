package mediaservice.models;


import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table(name = "video_items")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@DiscriminatorValue("VIDEO_ITEM")
public class VideoItem extends StoryItem{
    private String url;
    private String thumbnailUrl;
    private Long duration;
    private int width;
    private int height;
}
