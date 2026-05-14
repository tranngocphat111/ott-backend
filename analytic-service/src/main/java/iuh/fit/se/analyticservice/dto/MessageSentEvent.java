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
public class MessageSentEvent {
    @JsonProperty("event_id")
    @JsonAlias("eventId")
    private String eventId;

    @JsonProperty("message_id")
    @JsonAlias("messageId")
    private String messageId;

    @JsonProperty("user_id")
    @JsonAlias("userId")
    private String userId;

    @JsonProperty("message_type")
    @JsonAlias("messageType")
    private String messageType;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
