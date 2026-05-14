package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table(name = "story_musics")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class StoryMusic {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Long startTime;
    private Long endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @ManyToOne
    @JoinColumn(name = "music_id")
    private Music music;
}
