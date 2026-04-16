package mediaservice.services;

import lombok.RequiredArgsConstructor;
import mediaservice.configs.MediaUploadProperties;
import mediaservice.dtos.messages.MediaUploadJob;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaUploadJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MediaUploadProperties properties;

    public void publish(MediaUploadJob job) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                job
        );
    }
}
