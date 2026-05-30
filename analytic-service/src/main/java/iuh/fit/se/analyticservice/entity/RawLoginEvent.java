package iuh.fit.se.analyticservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "raw_login_events",
        indexes = {
                @Index(name = "idx_raw_login_events_timestamp", columnList = "event_timestamp"),
                @Index(name = "idx_raw_login_events_user_timestamp", columnList = "user_id,event_timestamp"),
                @Index(name = "idx_raw_login_events_method_timestamp", columnList = "login_method,event_timestamp")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawLoginEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "login_method", nullable = false)
    private String loginMethod;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;
}
