package moderationservice.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;
import software.amazon.awssdk.services.rekognition.model.S3Object;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RekognitionModerationProvider {

    private static final float MIN_CONFIDENCE = 75.0F;

    private final RekognitionClient rekognitionClient;

    public List<String> scanImage(String bucket, String objectKey) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Image moderation payload requires bucket");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Image moderation payload requires objectKey");
        }

        DetectModerationLabelsRequest request = DetectModerationLabelsRequest.builder()
                .minConfidence(MIN_CONFIDENCE)
                .image(Image.builder()
                        .s3Object(S3Object.builder()
                                .bucket(bucket.trim())
                                .name(objectKey.trim())
                                .build())
                        .build())
                .build();

        DetectModerationLabelsResponse response = rekognitionClient.detectModerationLabels(request);
        List<String> labels = response.moderationLabels().stream()
                .map(ModerationLabel::name)
                .distinct()
                .toList();

        log.info("AWS Rekognition image scan completed: bucket={}, objectKey={}, labels={}",
                bucket, objectKey, labels.size());
        return labels;
    }
}
