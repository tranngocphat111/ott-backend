package iuh.fit.userservice.service;

import iuh.fit.userservice.config.AwsS3Properties;
import iuh.fit.userservice.dto.response.PresignedUrlResponse;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final S3Presigner s3Presigner;
    private final AwsS3Properties props;

    private static final int EXPIRES_MINUTES = 5;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public PresignedUrlResponse generatePresignedUrl(String userId, String folder, String originalFilename) {
        validateFilename(originalFilename);

        String s3Key = buildS3Key(folder, userId, originalFilename);
        String contentType = resolveContentType(originalFilename);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(EXPIRES_MINUTES))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        // URL public sau khi upload
        String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s",
                props.getBucket(), props.getRegion(), s3Key);

        log.info("Presigned URL generated | userId: {} | folder: {} | key: {}", userId, folder, s3Key);

        return PresignedUrlResponse.builder()
                .uploadUrl(presigned.url().toString())
                .fileUrl(fileUrl)
                .s3Key(s3Key)
                .expiresInMinutes(EXPIRES_MINUTES)
                .contentType(contentType)
                .build();
    }


    private String buildS3Key(String folder, String userId, String originalFilename) {
        LocalDateTime now = LocalDateTime.now();
        String datePath = String.format("%d/%02d/%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
        String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return String.format("%s/%s/%s/%s_%s", folder, userId, datePath, timestamp, safeFilename);
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private String resolveContentType(String filename) {
        return switch (filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()) {
            case "png"  -> "image/png";
            case "webp" -> "image/webp";
            default     -> "image/jpeg";
        };
    }
}