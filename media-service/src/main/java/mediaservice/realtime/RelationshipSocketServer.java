package mediaservice.realtime;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "relationship.socket.enabled", havingValue = "true", matchIfMissing = true)
public class RelationshipSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(RelationshipSocketServer.class);

    @Value("${relationship.socket.host:0.0.0.0}")
    private String host;

    @Value("${relationship.socket.port:8091}")
    private Integer port;

    private SocketIOServer server;

    @PostConstruct
    public void start() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setOrigin("*");

        server = new SocketIOServer(config);

        server.addConnectListener(client ->
            logger.info("Relationship socket connected: {}", client.getSessionId())
        );
        server.addDisconnectListener(client ->
            logger.info("Relationship socket disconnected: {}", client.getSessionId())
        );

        try {
            server.start();
            logger.info("Relationship Socket.IO server started on {}:{}", host, port);
        } catch (Exception ex) {
            logger.error("Socket.IO server start failed at {}:{}; continuing without realtime.", host, port, ex);
            server = null;
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    public SocketIOServer getServer() {
        return server;
    }
}
