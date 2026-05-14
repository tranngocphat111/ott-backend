package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table(name = "media_musics")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class MediaMusic {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Long startTime;

    private Long endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @ManyToOne
    @JoinColumn(name = "music_id")
    private Music music;
}
