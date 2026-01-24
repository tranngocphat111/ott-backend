package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table(name = "note_musics")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class NoteMusic {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private Long startTime;
    private Long endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @ManyToOne
    @JoinColumn(name = "music_id")
    private Music music;
}
