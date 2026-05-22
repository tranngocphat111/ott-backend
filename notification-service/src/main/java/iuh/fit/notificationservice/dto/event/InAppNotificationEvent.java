package iuh.fit.notificationservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotificationEvent implements Serializable {
    private String recipientId;
    private String senderId;
    private String type; // FRIEND_REQUEST, POST_REACTION, PROFILE_UPDATE, etc.
    private String content;
    private String referenceId;
    private Boolean pushOnly;
}
