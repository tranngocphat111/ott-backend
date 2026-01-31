package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicRequest {
    private String title;
    private String artist;
    private String audioUrl;
    private Long duration;
    private Long startTime;  // For media music, story music, note music
    private Long endTime;    // For media music, story music, note music
}

