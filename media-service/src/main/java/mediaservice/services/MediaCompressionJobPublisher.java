package mediaservice.services;

import lombok.RequiredArgsConstructor;
import mediaservice.configs.MediaCompressionProperties;
import mediaservice.dtos.messages.MediaCompressionJob;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaCompressionJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MediaCompressionProperties properties;

    public void publish(MediaCompressionJob job) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                job
        );
    }
}
