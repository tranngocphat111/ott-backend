package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.messages.MediaDeleteJob;
import mediaservice.realtime.MediaRealtimePublisher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaDeleteJobConsumer {

    private final S3Service s3Service;
    private final MediaRealtimePublisher mediaRealtimePublisher;

    @RabbitListener(queues = "${media.delete.queue}")
    public void handleDelete(MediaDeleteJob job) {
        if (job == null || job.getS3Keys() == null || job.getS3Keys().isEmpty()) {
            log.warn("[MediaDelete] Empty job: {}", job);
            return;
        }

        List<String> keys = job.getS3Keys();
        boolean success = true;
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            boolean deleted = s3Service.deleteFile(key);
            if (!deleted) {
                success = false;
            }
        }

        if (!success) {
            throw new IllegalStateException("Failed to delete one or more S3 objects");
        }

        mediaRealtimePublisher.publish(
                job.getContentTargetType(),
                job.getContentId(),
                job.getOperation(),
                List.of(),
                keys
        );
    }
}
