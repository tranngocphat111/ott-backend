package mediaservice.mappers;

import mediaservice.utils.MediaUrlBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Base mapper with utility methods for URL conversion
 * All mappers that need to convert relative paths to full URLs should extend this
 */
public abstract class BaseMediaMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Value("${aws.s3.folder.posts:posts}")
    protected String postsFolder;

    @Value("${aws.s3.folder.stories:stories}")
    protected String storiesFolder;

    @Value("${aws.s3.folder.avatars:avatars}")
    protected String avatarsFolder;

    @Value("${aws.s3.folder.covers:covers}")
    protected String coversFolder;

    @Value("${aws.s3.folder.videos:videos}")
    protected String videosFolder;

    @Value("${aws.s3.folder.music:music}")
    protected String musicFolder;

    /**
     * Build full URL from relative path
     * Automatically detects if it's already a full URL
     */
    protected String buildFullUrl(String relativePathOrFileName, String folder) {
        if (relativePathOrFileName == null || relativePathOrFileName.isEmpty()) {
            return null;
        }

        // If already a full URL, return as-is
        if (mediaUrlBuilder.isFullUrl(relativePathOrFileName)) {
            return relativePathOrFileName;
        }

        // If contains folder separator, it's a relative path (e.g., "posts/uuid.jpg")
        if (relativePathOrFileName.contains("/")) {
            return mediaUrlBuilder.buildFullUrl("", relativePathOrFileName);
        }

        // Otherwise, it's just a filename - add folder
        return mediaUrlBuilder.buildFullUrl(folder, relativePathOrFileName);
    }

    /**
     * Extract relative path from full URL for saving to database
     */
    protected String extractRelativePath(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            return null;
        }

        return mediaUrlBuilder.extractRelativePath(fullUrl);
    }

    /**
     * Extract filename only from full URL
     */
    protected String extractFileName(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            return null;
        }

        return mediaUrlBuilder.extractFileName(fullUrl);
    }
}

