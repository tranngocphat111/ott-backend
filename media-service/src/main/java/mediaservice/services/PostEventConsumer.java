package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.realtime.RelationshipSocketServer;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostEventConsumer {

    private final ObjectProvider<RelationshipSocketServer> socketServerProvider;

    @RabbitListener(queues = "media_service_post_updates")
    public void handlePostEvent(Map<String, Object> payload, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            log.info("[PostEventConsumer] Received event: {} with routingKey: {}", payload, routingKey);
            
            RelationshipSocketServer socketServer = socketServerProvider.getIfAvailable();
            if (socketServer == null) {
                log.warn("[PostEventConsumer] RelationshipSocketServer not available");
                return;
            }

            SocketIOServer server = socketServer.getServer();
            if (server == null) {
                log.warn("[PostEventConsumer] SocketIOServer is not running");
                return;
            }

            if ("post.media.update".equals(routingKey)) {
                log.info("[PostEventConsumer] Broadcasting media_content_updated event to all clients on port 8091");
                server.getBroadcastOperations().sendEvent("media_content_updated", payload);
            } else if ("post.activity.update".equals(routingKey)) {
                log.info("[PostEventConsumer] Broadcasting post_activity_updated event to all clients on port 8091");
                server.getBroadcastOperations().sendEvent("post_activity_updated", payload);
            }
            
        } catch (Exception e) {
            log.error("[PostEventConsumer] Error processing event: {}", e.getMessage());
        }
    }
}
