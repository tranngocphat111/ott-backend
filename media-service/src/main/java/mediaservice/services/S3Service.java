package mediaservice.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface S3Service {

    /**
     * Upload file to S3
     * @param file MultipartFile to upload
     * @param folder Folder path in S3 bucket (e.g., "images", "videos")
     * @return URL of uploaded file
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * Upload file from InputStream to S3
     * @param inputStream InputStream of file
     * @param fileName Original filename
     * @param contentType Content type of file
     * @param folder Folder path in S3 bucket
     * @return URL of uploaded file
     */
    String uploadFile(InputStream inputStream, String fileName, String contentType, String folder);

    /**
     * Delete file from S3
     * @param fileUrl URL or key of file to delete
     * @return true if successful
     */
    boolean deleteFile(String fileUrl);

    /**
     * Get presigned URL for temporary access
     * @param fileKey S3 object key
     * @param expirationMinutes Expiration time in minutes
     * @return Presigned URL
     */
    String getPresignedUrl(String fileKey, int expirationMinutes);

    /**
     * Check if file exists
     * @param fileKey S3 object key
     * @return true if file exists
     */
    boolean fileExists(String fileKey);
}

