package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryUploadResponse;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.dtos.messages.MediaCompressionJob;
import mediaservice.dtos.messages.MediaUploadJob;
import mediaservice.services.MediaCompressionJobPublisher;
import mediaservice.services.MediaUploadJobPublisher;
import mediaservice.services.StoryService;
import mediaservice.utils.MediaTempFileStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;
    private final MediaCompressionJobPublisher mediaCompressionJobPublisher;
    private final MediaUploadJobPublisher mediaUploadJobPublisher;

    /** POST /stories - tao story moi */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StoryResponse> createStory(@RequestBody StoryRequest request) {
        return ResponseEntity.ok(storyService.createStory(request));
    }

    /** POST /stories/upload - upload story media before creating story */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryUploadResponse> uploadStoryMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "storyItemId", required = false) String storyItemId) {
        String extension = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }

        String shortToken = Long.toString(System.currentTimeMillis(), 36)
                + "-" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = shortToken + extension;
        String fileKey = "stories/" + fileName;

        enqueueAsyncMediaProcessing(file, fileKey, storyItemId);

        return ResponseEntity.ok(new StoryUploadResponse(null, fileKey));
    }

    private void enqueueAsyncMediaProcessing(MultipartFile file, String s3Key, String storyItemId) {
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        boolean isVideo = contentType.startsWith("video/");
        boolean isAudio = contentType.startsWith("audio/");

        try {
            if (isVideo || isAudio) {
                String mediaType = isAudio ? "AUDIO" : "VIDEO";
                String outputContentType = isAudio ? "audio/mp4" : "video/mp4";
                String prefix = isAudio ? "audio-" : "video-";

                java.nio.file.Path tempPath = MediaTempFileStore.saveToTemp(file, prefix);
                MediaCompressionJob job = new MediaCompressionJob(
                    tempPath.toString(),
                    mediaType,
                    s3Key,
                    outputContentType,
                    null,
                    "STORY",
                    "UPLOAD",
                    storyItemId,
                    null
                );
                mediaCompressionJobPublisher.publish(job);
                return;
            }

            java.nio.file.Path tempPath = MediaTempFileStore.saveToTemp(file, "image-");
            MediaUploadJob job = new MediaUploadJob(
                    tempPath.toString(),
                    s3Key,
                    contentType,
                    null,
                    "STORY",
                    "UPLOAD",
                    storyItemId,
                    null
            );
            mediaUploadJobPublisher.publish(job);
        } catch (Exception ex) {
            // Ignore enqueue failures to keep upload flow stable.
        }
    }

    /** GET /stories - lay tat ca stories */
    @GetMapping
    public ResponseEntity<List<StoryResponse>> getAllStories() {
        return ResponseEntity.ok(storyService.getAllStories());
    }

    /**
     * GET /stories/reel/{accountId}?suggestionLimit=8
     * - Return authorized active stories for accountId.
     * - If no active stories remain, return suggested users for friend recommendations.
     */
    @GetMapping("/reel/{accountId}")
    public ResponseEntity<StoryReelResponse> getStoriesReel(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "8") int suggestionLimit) {
        return ResponseEntity.ok(storyService.getStoriesReel(accountId, suggestionLimit));
    }
}
