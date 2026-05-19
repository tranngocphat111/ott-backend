package mediaservice.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MediaRealtimePublisher {

    private static final String EVENT_NAME = "media_content_updated";
    private static final String EXCHANGE_NAME = "post.events";
    private static final String ROUTING_KEY = "post.media.update";

    private final ObjectProvider<RelationshipSocketServer> socketServerProvider;
    private final RabbitTemplate rabbitTemplate;

    public MediaRealtimePublisher(ObjectProvider<RelationshipSocketServer> socketServerProvider, RabbitTemplate rabbitTemplate) {
        this.socketServerProvider = socketServerProvider;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(
            String contentTargetType,
            String contentId,
            String operation,
            List<MediaRealtimeUpdate> updates,
            List<String> keys) {
        if (contentId == null || contentId.isBlank()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contentId", contentId);
        payload.put("contentTargetType", contentTargetType);
        payload.put("operation", operation);
        payload.put("mediaUpdates", updates != null ? updates : List.of());
        payload.put("s3Keys", keys != null ? keys : List.of());
        payload.put("status", "DONE");
        payload.put("timestamp", Instant.now().toString());

        // Publish to RabbitMQ exchange for clustered/unified sync
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, payload);
        } catch (Exception ex) {
            // Keep going even if RabbitMQ fails
        }

        RelationshipSocketServer socketServer = socketServerProvider.getIfAvailable();
        if (socketServer == null) {
            return;
        }

        SocketIOServer server = socketServer.getServer();
        if (server != null) {
            server.getBroadcastOperations().sendEvent(EVENT_NAME, payload);
        }
    }
}
