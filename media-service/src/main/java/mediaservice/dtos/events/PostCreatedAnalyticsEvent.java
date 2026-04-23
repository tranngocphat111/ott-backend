package mediaservice.dtos.events;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCreatedAnalyticsEvent {
    private String event_id;
    private String post_id;
    private String user_id;
    private Instant timestamp;
}
