package mediaservice.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PostActivityPublisher {

    private static final String EVENT_NAME = "post_activity_updated";
    private final ObjectProvider<RelationshipSocketServer> socketServerProvider;

    public PostActivityPublisher(ObjectProvider<RelationshipSocketServer> socketServerProvider) {
        this.socketServerProvider = socketServerProvider;
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

        RelationshipSocketServer socketServer = socketServerProvider.getIfAvailable();
        if (socketServer != null) {
            SocketIOServer server = socketServer.getServer();
            if (server != null) {
                server.getBroadcastOperations().sendEvent(EVENT_NAME, payload);
            }
        }
    }
}
