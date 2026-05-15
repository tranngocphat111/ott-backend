package iuh.fit.se.analyticservice.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserStatusChangedEvent {

    @JsonProperty("event_id")
    @JsonAlias("eventId")
    private String eventId;

    @JsonProperty("user_id")
    @JsonAlias("userId")
    private String userId;

    @JsonProperty("old_status")
    @JsonAlias("oldStatus")
    private String oldStatus;

    @JsonProperty("new_status")
    @JsonAlias("newStatus")
    private String newStatus;

    @JsonProperty("admin_id")
    @JsonAlias("adminId")
    private String adminId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("duration_minutes")
    @JsonAlias("durationMinutes")
    private Long durationMinutes;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
