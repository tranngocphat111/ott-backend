package mediaservice.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Table(name = "video_medias")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@DiscriminatorValue("VIDEO_MEDIA")
@ToString(callSuper = true)
public class VideoMedia extends Media{
    private String thumbnailUrl;
    private Long duration;

    private boolean hasAudio;
}
