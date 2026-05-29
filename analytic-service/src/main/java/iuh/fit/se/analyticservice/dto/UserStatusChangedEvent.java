package iuh.fit.se.analyticservice.dto;

import java.time.Instant;
import java.time.LocalDateTime;

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

    @JsonProperty("actionType")
    @JsonAlias("action_type")
    private String actionType;

    @JsonProperty("actorId")
    @JsonAlias({"actor_id", "adminId", "admin_id"})
    private String actorId;

    @JsonProperty("actorRole")
    @JsonAlias("actor_role")
    private String actorRole;

    @JsonProperty("previousStatus")
    @JsonAlias("previous_status")
    private UserStatusSnapshot previousStatus;

    @JsonProperty("newStatus")
    @JsonAlias("new_status_snapshot")
    private UserStatusSnapshot newStatusSnapshot;

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

    @JsonProperty("effectiveUntil")
    @JsonAlias("effective_until")
    private LocalDateTime effectiveUntil;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
