package mediaservice.services.impl;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.services.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final AmazonS3 amazonS3;
    private final TransferManager transferManager;

    @Value("${aws.social.s3.bucket-name}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            long fileSize = file.getSize();

            return uploadFile(inputStream, originalFilename, contentType, folder, fileSize, false);
        } catch (IOException e) {
            log.error("Error reading file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String folder, String fileName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            String contentType = file.getContentType();
            long fileSize = file.getSize();

            return uploadFile(inputStream, fileName, contentType, folder, fileSize, true);
        } catch (IOException e) {
            log.error("Error reading file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String fileName, String contentType, String folder) {
        return uploadFile(inputStream, fileName, contentType, folder, -1, true);
    }

    /**
     * Internal upload with optional content length for efficient streaming.
     * Pass fileSize = -1 when size is unknown.
     */
    private String uploadFile(InputStream inputStream, String fileName, String contentType,
            String folder, long fileSize, boolean keepFileName) {
        try {
            // Generate unique filename
            String fileExtension = getFileExtension(fileName);
            String uniqueFileName = keepFileName && fileName != null && !fileName.isBlank()
                    ? fileName
                    : UUID.randomUUID() + fileExtension;
            String fileKey = folder + "/" + uniqueFileName;

            // Set metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            if (fileSize > 0) {
                metadata.setContentLength(fileSize); // avoids SDK in-memory buffering
            }

            // Multipart upload via TransferManager for faster throughput.
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    fileKey,
                    inputStream,
                    metadata);

            Upload upload = transferManager.upload(putObjectRequest);
            upload.waitForCompletion();

            log.info("File uploaded successfully: {}", fileKey);
            return fileKey; // Return relative key; PostMapper converts to full URL

        } catch (AmazonServiceException e) {
            log.error("Error uploading file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        } catch (Exception e) {
            log.error("Error uploading file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public boolean deleteFile(String fileUrl) {
        try {
            // Extract file key from URL
            String fileKey = extractKeyFromUrl(fileUrl);

            if (fileKey == null || fileKey.isEmpty()) {
                log.warn("Invalid file URL: {}", fileUrl);
                return false;
            }

            amazonS3.deleteObject(bucketName, fileKey);
            log.info("File deleted successfully: {}", fileKey);
            return true;

        } catch (AmazonServiceException e) {
            log.error("Error deleting file from S3: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String fileKey, int expirationMinutes) {
        try {
            Date expiration = new Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += 1000L * 60 * expirationMinutes;
            expiration.setTime(expTimeMillis);

            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName,
                    fileKey)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(expiration);

            URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
            return url.toString();

        } catch (AmazonServiceException e) {
            log.error("Error generating presigned URL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    @Override
    public boolean fileExists(String fileKey) {
        try {
            return amazonS3.doesObjectExist(bucketName, fileKey);
        } catch (AmazonServiceException e) {
            log.error("Error checking file existence: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getFullUrl(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return null;
        }

        // If already a full URL, return as-is
        if (fileKey.startsWith("http://") || fileKey.startsWith("https://")) {
            return fileKey;
        }

        // Build full URL from file key
        return amazonS3.getUrl(bucketName, fileKey).toString();
    }

    /**
     * Extract file key from S3 URL
     */
    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        // Handle different URL formats
        // Format 1: https://bucket-name.s3.region.amazonaws.com/folder/file.ext
        // Format 2: https://s3.region.amazonaws.com/bucket-name/folder/file.ext

        try {
            if (fileUrl.contains(bucketName)) {
                int bucketIndex = fileUrl.indexOf(bucketName);
                int keyStartIndex = bucketIndex + bucketName.length() + 1; // +1 for the '/'
                if (keyStartIndex < fileUrl.length()) {
                    return fileUrl.substring(keyStartIndex);
                }
            }

            // If URL doesn't contain bucket name, assume it's already a key
            return fileUrl;

        } catch (Exception e) {
            log.error("Error extracting key from URL: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex);
        }

        return "";
    }
}
