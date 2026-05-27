package mediaservice.services;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsRequest;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import com.amazonaws.services.rekognition.model.S3Object;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageModerationService {

    private final AmazonRekognition amazonRekognition;
    private final S3Service s3Service;

    @Value("${aws.social.s3.bucket-name}")
    private String bucketName;

    @Value("${profile.image.moderation.enabled:true}")
    private boolean moderationEnabled;

    @Value("${profile.image.moderation.min-confidence:75}")
    private Float minConfidence;

    public void assertSafeProfileImage(String objectKey) {
        if (!moderationEnabled) {
            return;
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile image key is required");
        }

        try {
            DetectModerationLabelsRequest request = new DetectModerationLabelsRequest()
                    .withMinConfidence(minConfidence)
                    .withImage(new Image().withS3Object(new S3Object()
                            .withBucket(bucketName)
                            .withName(objectKey.trim())));

            List<String> labels = amazonRekognition.detectModerationLabels(request)
                    .getModerationLabels()
                    .stream()
                    .map(ModerationLabel::getName)
                    .distinct()
                    .toList();

            if (labels.isEmpty()) {
                return;
            }

            s3Service.deleteFile(objectKey);
            log.warn("Blocked unsafe profile image: objectKey={}, labels={}", objectKey, labels);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Profile image violates moderation policy: " + String.join(", ", labels)
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            s3Service.deleteFile(objectKey);
            log.error("Profile image moderation failed: objectKey={}", objectKey, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile image moderation is unavailable",
                    ex
            );
        }
    }
}
