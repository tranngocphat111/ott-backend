package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.UserRegisteredEvent;
import iuh.fit.se.analyticservice.entity.RawUserEvent;
import iuh.fit.se.analyticservice.repository.RawUserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final RawUserEventRepository rawUserEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.USER_REGISTERED_QUEUE)
    public void handleUserRegisteredEvent(Message message) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);

            RawUserEvent raw = new RawUserEvent(
                    event.getEventId(),
                    event.getUserId(),
                    event.getRegisterMethod(),
                    event.getTimestamp()
            );

            rawUserEventRepository.save(raw);
            log.info("Saved registration event: eventId={}, userId={}", event.getEventId(), event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to parse/save registration event payload", ex);
        }
    }
}
