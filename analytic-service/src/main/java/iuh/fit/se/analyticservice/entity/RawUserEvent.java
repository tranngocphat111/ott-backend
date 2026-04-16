package iuh.fit.se.analyticservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raw_user_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawUserEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "register_method", nullable = false)
    private String registerMethod;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;
}
