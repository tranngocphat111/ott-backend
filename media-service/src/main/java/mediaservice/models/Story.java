package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Table(name = "stories")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@DiscriminatorValue("STORY")
public class Story extends Content{

    private boolean isHighlight;

    private String highlightName;

    @OneToMany(mappedBy = "story")
    private Set<StoryMusic> storyMusics;

    private LocalDateTime expireAt;

    @OneToMany(mappedBy = "story")
    private Set<StoryItem> storyItems;
}
