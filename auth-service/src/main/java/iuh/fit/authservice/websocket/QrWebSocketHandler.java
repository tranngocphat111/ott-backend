package iuh.fit.authservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.authservice.dto.response.QrStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class QrWebSocketHandler extends TextWebSocketHandler {

    // qrId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String qrId = getQrIdFromSession(session);
        if (qrId != null) {
            sessions.put(qrId, session);
            log.info("WebSocket connection established for QR ID: {}", qrId);
        } else {
            log.warn("WebSocket connection established without QR ID. Closing...");
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String qrId = getQrIdFromSession(session);
        if (qrId != null) {
            sessions.remove(qrId);
            log.info("WebSocket connection closed for QR ID: {}", qrId);
        }
    }

    public void sendQrStatusUpdate(String qrId, QrStatusResponse status) {
        WebSocketSession session = sessions.get(qrId);
        if (session != null && session.isOpen()) {
            try {
                String payload = objectMapper.writeValueAsString(status);
                session.sendMessage(new TextMessage(payload));
                log.info("Sent QR status update for QR ID: {}", qrId);
            } catch (IOException e) {
                log.error("Failed to send QR status update to WebSocket", e);
            }
        }
    }

    private String getQrIdFromSession(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("qrId");
    }
}
