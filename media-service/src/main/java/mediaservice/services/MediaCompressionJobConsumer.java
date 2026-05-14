package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.messages.MediaCompressionJob;
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
public class MediaCompressionJobConsumer {

    private final MediaCompressionService mediaCompressionService;
    private final S3Service s3Service;
    private final MediaRealtimePublisher mediaRealtimePublisher;

    @RabbitListener(queues = "${media.compression.queue}")
    public void handleCompression(MediaCompressionJob job) throws Exception {
        if (job == null || job.getTempPath() == null || job.getMediaType() == null) {
            log.warn("[MediaCompression] Invalid job: {}", job);
            return;
        }

        Path inputPath = Path.of(job.getTempPath());
        Path outputPath = null;
        try {
            if (!Files.exists(inputPath)) {
                log.warn("[MediaCompression] Temp file not found: {}", inputPath);
                return;
            }

            if ("AUDIO".equalsIgnoreCase(job.getMediaType())) {
                outputPath = mediaCompressionService.compressAudio(inputPath);
            } else {
                outputPath = mediaCompressionService.compressVideo(inputPath);
            }

            if (job.getS3Key() != null && !job.getS3Key().isBlank()) {
                uploadToS3(job, outputPath);
                mediaRealtimePublisher.publish(
                        job.getContentTargetType(),
                        job.getContentId(),
                        job.getOperation(),
                        List.of(new MediaRealtimeUpdate(job.getMediaId(), job.getS3Key(), job.getOrderIndex())),
                        List.of(job.getS3Key()));
            }
        } catch (Exception ex) {
            log.error("[MediaCompression] Failed job: {}", job, ex);
            throw ex;
        } finally {
            deleteQuietly(inputPath);
            deleteQuietly(outputPath);
        }
    }

    private void uploadToS3(MediaCompressionJob job, Path outputPath) throws Exception {
        if (outputPath == null || !Files.exists(outputPath)) {
            throw new IllegalStateException("Compressed output not found");
        }

        String s3Key = job.getS3Key();
        int lastSlash = s3Key.lastIndexOf('/');
        String folder = lastSlash > 0 ? s3Key.substring(0, lastSlash) : "";
        String fileName = lastSlash > 0 ? s3Key.substring(lastSlash + 1) : s3Key;

        String contentType = job.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "AUDIO".equalsIgnoreCase(job.getMediaType()) ? "audio/mp4" : "video/mp4";
        }

        try (InputStream inputStream = Files.newInputStream(outputPath)) {
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
            log.warn("[MediaCompression] Failed to delete temp file: {}", path);
        }
    }
}
