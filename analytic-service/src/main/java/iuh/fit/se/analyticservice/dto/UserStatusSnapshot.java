package iuh.fit.se.analyticservice.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserStatusSnapshot {

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("isBlocked")
    private Boolean isBlocked;

    @JsonProperty("blockedUntil")
    private LocalDateTime blockedUntil;

    @JsonProperty("blockedReason")
    private String blockedReason;

    @JsonProperty("deletedAt")
    private LocalDateTime deletedAt;
}
