package mediaservice.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import mediaservice.models.Relationship;
import mediaservice.services.RelationshipEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class RelationshipRealtimePublisher {

    private static final String EVENT_NAME = "cap_nhat_quan_he";

    private final ObjectProvider<RelationshipSocketServer> socketServerProvider;
    private final RelationshipEventPublisher eventPublisher;

    public RelationshipRealtimePublisher(
            ObjectProvider<RelationshipSocketServer> socketServerProvider,
            RelationshipEventPublisher eventPublisher) {
        this.socketServerProvider = socketServerProvider;
        this.eventPublisher = eventPublisher;
    }

    public void publish(String type, Relationship relationship, String actorId) {
        if (relationship == null) {
            return;
        }

        Set<String> targets = new LinkedHashSet<>();
        if (relationship.getRequester() != null) {
            targets.add(relationship.getRequester().getId());
        }
        if (relationship.getReceiver() != null) {
            targets.add(relationship.getReceiver().getId());
        }

        if ("BLOCKED".equals(type) || "USER_BLOCKED".equals(type)) {
            targets.clear();
            if (actorId != null) {
                targets.add(actorId);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("relationshipId", relationship.getId());
        payload.put("requesterId", relationship.getRequester() != null ? relationship.getRequester().getId() : null);
        payload.put("receiverId", relationship.getReceiver() != null ? relationship.getReceiver().getId() : null);
        payload.put("status", relationship.getStatus() != null ? relationship.getStatus().name() : null);
        payload.put("actorId", actorId);
        payload.put("timestamp", Instant.now().toString());
        payload.put("targetUserIds", targets.stream().toList());

        RelationshipSocketServer socketServer = socketServerProvider.getIfAvailable();
        if (socketServer != null) {
            SocketIOServer server = socketServer.getServer();
            if (server != null) {
                server.getBroadcastOperations().sendEvent(EVENT_NAME, payload);
            }
        }

        // Publish to RabbitMQ for cross-service sync
        eventPublisher.publish(type, relationship, actorId);
    }

    public void publishToSocketOnly(String type, Relationship relationship, String actorId) {
        if (relationship == null) return;

        Set<String> targets = new LinkedHashSet<>();
        if (relationship.getRequester() != null) targets.add(relationship.getRequester().getId());
        if ("BLOCKED".equals(type) || "USER_BLOCKED".equals(type)) {
            targets.clear();
            if (actorId != null) {
                targets.add(actorId);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("relationshipId", relationship.getId());
        payload.put("requesterId", relationship.getRequester() != null ? relationship.getRequester().getId() : null);
        payload.put("receiverId", relationship.getReceiver() != null ? relationship.getReceiver().getId() : null);
        payload.put("status", relationship.getStatus() != null ? relationship.getStatus().name() : null);
        payload.put("actorId", actorId);
        payload.put("timestamp", Instant.now().toString());
        payload.put("targetUserIds", targets.stream().toList());

        RelationshipSocketServer socketServer = socketServerProvider.getIfAvailable();
        if (socketServer != null && socketServer.getServer() != null) {
            socketServer.getServer().getBroadcastOperations().sendEvent(EVENT_NAME, payload);
        }
    }

    public void publishAfterCommit(String type, Relationship relationship, String actorId) {
        if (relationship == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publish(type, relationship, actorId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(type, relationship, actorId);
            }
        });
    }
}
