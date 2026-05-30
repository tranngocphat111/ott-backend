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
        name = "raw_message_events",
        indexes = {
                @Index(name = "idx_raw_message_events_timestamp", columnList = "event_timestamp"),
                @Index(name = "idx_raw_message_events_user_timestamp", columnList = "user_id,event_timestamp"),
                @Index(name = "idx_raw_message_events_type_timestamp", columnList = "message_type,event_timestamp")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawMessageEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;
}
