package iuh.fit.notificationservice.consumer;

import iuh.fit.notificationservice.dto.event.InAppNotificationEvent;
import iuh.fit.notificationservice.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RelationshipNotificationConsumer {

    private final InAppNotificationService inAppNotificationService;

    @RabbitListener(queues = "${rabbitmq.queue.relationship}")
    public void handleRelationshipEvent(Map<String, Object> payload) {
        try {
            log.info("[RabbitMQ] Relationship event: {}", payload);
            InAppNotificationEvent event = buildNotificationEvent(payload);
            if (event == null || event.getRecipientId() == null) {
                log.warn("[RabbitMQ] Ignored relationship event without recipient: {}", payload);
                return;
            }
            inAppNotificationService.processNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process relationship notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private InAppNotificationEvent buildNotificationEvent(Map<String, Object> payload) {
        String type = upper(payload.get("type"));
        String relationshipId = string(payload.get("relationshipId"));
        String requesterId = string(payload.get("requesterId"));
        String receiverId = string(payload.get("receiverId"));
        String actorId = firstNonBlank(
                string(payload.get("actorId")),
                string(payload.get("blockedById"))
        );

        String requesterName = firstNonBlank(
                string(payload.get("requesterDisplayName")),
                requesterId,
                "Người dùng"
        );
        String receiverName = firstNonBlank(
                string(payload.get("receiverDisplayName")),
                receiverId,
                "Người dùng"
        );
        String blockerName = firstNonBlank(
                string(payload.get("blockedByDisplayName")),
                actorId,
                "Người dùng"
        );

        if (type == null || requesterId == null || receiverId == null) {
            return null;
        }

        return switch (type) {
            case "REQUEST_SENT" -> InAppNotificationEvent.builder()
                    .recipientId(receiverId)
                    .senderId(requesterId)
                    .type("FRIEND_REQUEST")
                    .content(requesterName + " đã gửi lời mời kết bạn")
                    .title("Lời mời kết bạn")
                    .referenceId(relationshipId)
                    .build();
            case "REQUEST_ACCEPTED" -> InAppNotificationEvent.builder()
                    .recipientId(requesterId)
                    .senderId(receiverId)
                    .type("FRIEND_REQUEST_ACCEPTED")
                    .content(receiverName + " đã chấp nhận lời mời kết bạn")
                    .title("Kết bạn thành công")
                    .referenceId(relationshipId)
                    .build();
            case "REQUEST_REJECTED" -> InAppNotificationEvent.builder()
                    .recipientId(requesterId)
                    .senderId(receiverId)
                    .type("FRIEND_REQUEST_REJECTED")
                    .content(receiverName + " đã từ chối lời mời kết bạn")
                    .title("Lời mời kết bạn")
                    .referenceId(relationshipId)
                    .build();
            case "BLOCKED" -> {
                yield null;
            }
            default -> null;
        };
    }

    private String determineBlockedRecipientId(String actorId, String requesterId, String receiverId) {
        if (actorId == null) return null;
        if (actorId.equals(requesterId)) return receiverId;
        if (actorId.equals(receiverId)) return requesterId;
        return null;
    }

    private String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String upper(Object value) {
        String text = string(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
