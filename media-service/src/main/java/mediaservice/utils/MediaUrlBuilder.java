package mediaservice.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for building full media URLs from relative paths
 * Supports both S3 direct URLs and CDN URLs
 */
@Component
@Slf4j
public class MediaUrlBuilder {

    @Value("${aws.s3.base-url}")
    private String baseUrl;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    /**
     * Build full URL from folder and filename
     * @param folder Folder path (e.g., "posts", "avatars")
     * @param fileName File name (e.g., "uuid.jpg")
     * @return Full URL
     */
    public String buildFullUrl(String folder, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // If already a full URL, return as-is
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            return fileName;
        }

        // Build full URL
        String cleanFolder = folder != null ? folder.trim() : "";
        String cleanFileName = fileName.trim();

        if (cleanFolder.isEmpty()) {
            return baseUrl + "/" + cleanFileName;
        }

        return baseUrl + "/" + cleanFolder + "/" + cleanFileName;
    }

    /**
     * Build full URL with automatic base URL construction
     * @param folder Folder path
     * @param fileName File name
     * @return Full URL
     */
    public String buildS3Url(String folder, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // If already a full URL, return as-is
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            return fileName;
        }

        // Construct S3 URL format: https://bucket-name.s3.region.amazonaws.com/folder/file
        String s3BaseUrl = String.format("https://%s.s3.%s.amazonaws.com", bucketName, region);

        String cleanFolder = folder != null ? folder.trim() : "";
        String cleanFileName = fileName.trim();

        if (cleanFolder.isEmpty()) {
            return s3BaseUrl + "/" + cleanFileName;
        }

        return s3BaseUrl + "/" + cleanFolder + "/" + cleanFileName;
    }

    /**
     * Extract filename from full URL
     * @param fullUrl Full URL
     * @return Filename only
     */
    public String extractFileName(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            return null;
        }

        // If not a URL, assume it's already a filename
        if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
            return fullUrl;
        }

        try {
            // Extract last part after last slash
            int lastSlashIndex = fullUrl.lastIndexOf('/');
            if (lastSlashIndex != -1 && lastSlashIndex < fullUrl.length() - 1) {
                return fullUrl.substring(lastSlashIndex + 1);
            }
            return fullUrl;
        } catch (Exception e) {
            log.error("Error extracting filename from URL: {}", e.getMessage());
            return fullUrl;
        }
    }

    /**
     * Extract relative path (folder + filename) from full URL
     * @param fullUrl Full URL
     * @return Relative path (e.g., "posts/uuid.jpg")
     */
    public String extractRelativePath(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            return null;
        }

        // If not a URL, assume it's already a relative path
        if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
            return fullUrl;
        }

        try {
            // Find bucket name in URL and extract everything after it
            if (fullUrl.contains(bucketName)) {
                int bucketIndex = fullUrl.indexOf(bucketName);
                int pathStartIndex = bucketIndex + bucketName.length() + 1; // +1 for the '/'
                if (pathStartIndex < fullUrl.length()) {
                    return fullUrl.substring(pathStartIndex);
                }
            }

            // Fallback: just get filename
            return extractFileName(fullUrl);
        } catch (Exception e) {
            log.error("Error extracting relative path from URL: {}", e.getMessage());
            return extractFileName(fullUrl);
        }
    }

    /**
     * Check if a string is a full URL
     * @param url String to check
     * @return true if it's a full URL
     */
    public boolean isFullUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }
}

