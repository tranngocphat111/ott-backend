package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.messages.MediaUploadJob;
import mediaservice.realtime.MediaRealtimePublisher;
import mediaservice.realtime.MediaRealtimeUpdate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadJobConsumer {

    private final S3Service s3Service;
    private final MediaRealtimePublisher mediaRealtimePublisher;

    @RabbitListener(queues = "${media.upload.queue}")
    public void handleUpload(MediaUploadJob job) throws Exception {
        if (job == null || job.getTempPath() == null || job.getS3Key() == null) {
            log.warn("[MediaUpload] Invalid job: {}", job);
            return;
        }

        Path inputPath = Path.of(job.getTempPath());
        if (!Files.exists(inputPath)) {
            log.warn("[MediaUpload] Temp file not found: {}", inputPath);
            return;
        }

        try {
            uploadToS3(job, inputPath);
            mediaRealtimePublisher.publish(
                    job.getContentTargetType(),
                    job.getContentId(),
                    job.getOperation(),
                    List.of(new MediaRealtimeUpdate(job.getMediaId(), job.getS3Key(), job.getOrderIndex())),
                    List.of(job.getS3Key())
            );
        } catch (Exception ex) {
            log.error("[MediaUpload] Failed job: {}", job, ex);
            throw ex;
        } finally {
            deleteQuietly(inputPath);
        }
    }

    private void uploadToS3(MediaUploadJob job, Path inputPath) throws Exception {
        String s3Key = job.getS3Key();
        int lastSlash = s3Key.lastIndexOf('/');
        String folder = lastSlash > 0 ? s3Key.substring(0, lastSlash) : "";
        String fileName = lastSlash > 0 ? s3Key.substring(lastSlash + 1) : s3Key;

        String contentType = job.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        try (InputStream inputStream = Files.newInputStream(inputPath)) {
            s3Service.uploadFile(inputStream, fileName, contentType, folder);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.warn("[MediaUpload] Failed to delete temp file: {}", path);
        }
    }
}
