package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.UserLoginEvent;
import iuh.fit.se.analyticservice.entity.RawLoginEvent;
import iuh.fit.se.analyticservice.repository.RawLoginEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserLoginEventListener {

    private final RawLoginEventRepository rawLoginEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.USER_LOGIN_QUEUE)
    public void handleUserLoginEvent(Message message) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            UserLoginEvent event = objectMapper.readValue(payload, UserLoginEvent.class);

            RawLoginEvent raw = new RawLoginEvent(
                    event.getEventId(),
                    event.getUserId(),
                    event.getLoginMethod(),
                    event.getTimestamp()
            );

            rawLoginEventRepository.save(raw);
            log.info("Saved login event: eventId={}, userId={}", event.getEventId(), event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to parse/save login event payload", ex);
        }
    }
}