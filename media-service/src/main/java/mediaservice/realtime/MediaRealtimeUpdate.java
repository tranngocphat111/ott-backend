package mediaservice.realtime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaRealtimeUpdate {
    private String mediaId;
    private String s3Key;
    private Integer orderIndex;
}