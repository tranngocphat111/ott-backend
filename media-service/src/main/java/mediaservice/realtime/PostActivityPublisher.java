package mediaservice.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PostActivityPublisher {

    private static final String EVENT_NAME = "post_activity_updated";
    private static final String EXCHANGE_NAME = "post.events";
    private static final String ROUTING_KEY = "post.activity.update";

    private final ObjectProvider<RelationshipSocketServer> socketServerProvider;
    private final RabbitTemplate rabbitTemplate;

    public PostActivityPublisher(ObjectProvider<RelationshipSocketServer> socketServerProvider, RabbitTemplate rabbitTemplate) {
        this.socketServerProvider = socketServerProvider;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String postId, String activityType, String action, Object data) {
        if (postId == null || postId.isBlank()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postId);
        payload.put("activityType", activityType);
        payload.put("action", action);
        if (data != null) {
            payload.put("data", data);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(payload);
                }
            });
            return;
        }

        send(payload);
    }

    private void send(Map<String, Object> payload) {
        // Publish to RabbitMQ exchange for clustered/unified sync
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, payload);
        } catch (Exception ex) {
            // Keep going even if RabbitMQ fails
        }

        RelationshipSocketServer socketServer = socketServerProvider.getIfAvailable();
        if (socketServer != null) {
            SocketIOServer server = socketServer.getServer();
            if (server != null) {
                server.getBroadcastOperations().sendEvent(EVENT_NAME, payload);
            }
        }
    }
}
