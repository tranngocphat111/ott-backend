package mediaservice.services;

import lombok.RequiredArgsConstructor;
import mediaservice.configs.MediaDeleteProperties;
import mediaservice.dtos.messages.MediaDeleteJob;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaDeleteJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MediaDeleteProperties properties;

    public void publish(MediaDeleteJob job) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                job
        );
    }
}
