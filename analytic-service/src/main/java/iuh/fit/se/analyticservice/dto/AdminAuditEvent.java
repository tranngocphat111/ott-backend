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
public class AdminAuditEvent {

    @JsonProperty("eventId")
    @JsonAlias("event_id")
    private String eventId;

    @JsonProperty("actorId")
    @JsonAlias({"actor_id", "adminId", "admin_id"})
    private String actorId;

    @JsonProperty("actionType")
    @JsonAlias("action_type")
    private String actionType;

    @JsonProperty("targetType")
    @JsonAlias("target_type")
    private String targetType;

    @JsonProperty("targetId")
    @JsonAlias("target_id")
    private String targetId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("oldValue")
    @JsonAlias("old_value")
    private String oldValue;

    @JsonProperty("newValue")
    @JsonAlias("new_value")
    private String newValue;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
