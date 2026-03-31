package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryUploadResponse;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.services.S3Service;
import mediaservice.services.StoryService;
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
    private final S3Service s3Service;

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
        String fileKey = s3Service.uploadFile(file, "stories", fileName);

        return ResponseEntity.ok(new StoryUploadResponse(null, fileKey));
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
