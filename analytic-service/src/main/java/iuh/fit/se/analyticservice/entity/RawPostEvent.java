package iuh.fit.se.analyticservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "raw_post_events",
        indexes = {
                @Index(name = "idx_raw_post_events_timestamp", columnList = "event_timestamp"),
                @Index(name = "idx_raw_post_events_user_timestamp", columnList = "user_id,event_timestamp")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawPostEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "post_id", nullable = false)
    private String postId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;
}
