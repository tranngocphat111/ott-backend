package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.services.StoryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    /** POST /stories - tao story moi */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StoryResponse> createStory(@RequestBody StoryRequest request) {
        return ResponseEntity.ok(storyService.createStory(request));
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
