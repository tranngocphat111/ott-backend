package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.MessageSentEvent;
import iuh.fit.se.analyticservice.entity.RawMessageEvent;
import iuh.fit.se.analyticservice.repository.RawMessageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSentEventListener {

    private final RawMessageEventRepository rawMessageEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.MESSAGE_SENT_QUEUE)
    public void handleMessageSent(Message message) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            MessageSentEvent event = objectMapper.readValue(payload, MessageSentEvent.class);

            RawMessageEvent raw = new RawMessageEvent(
                    event.getEventId(),
                    event.getMessageId(),
                    event.getUserId(),
                    event.getMessageType(),
                    event.getTimestamp()
            );

            rawMessageEventRepository.save(raw);
            log.info("Saved message event: eventId={}, messageType={}", raw.getEventId(), raw.getMessageType());
        } catch (Exception ex) {
            log.error("Failed to parse/save message sent event", ex);
        }
    }
}
