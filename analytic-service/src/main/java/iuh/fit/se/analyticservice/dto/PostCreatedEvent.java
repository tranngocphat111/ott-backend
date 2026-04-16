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
public class PostCreatedEvent {
    @JsonProperty("event_id")
    @JsonAlias("eventId")
    private String eventId;

    @JsonProperty("post_id")
    @JsonAlias("postId")
    private String postId;

    @JsonProperty("user_id")
    @JsonAlias("userId")
    private String userId;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
