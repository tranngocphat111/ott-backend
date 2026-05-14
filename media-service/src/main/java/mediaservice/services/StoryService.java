package mediaservice.services;

import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StoryService {
    StoryResponse createStory(StoryRequest request);
    StoryResponse getStoryById(String id);
    List<StoryResponse> getAllStories();
    Page<StoryResponse> getAllStories(Pageable pageable);
    StoryResponse updateStory(String id, StoryRequest request, java.util.List<org.springframework.web.multipart.MultipartFile> files, java.util.List<String> captions);
    void deleteStory(String id);
    List<StoryResponse> getStoriesByUserId(String userId);
    List<StoryResponse> getActiveStories();
    List<StoryResponse> getAuthorizedActiveStories(String accountId);
    StoryReelResponse getStoriesReel(String accountId, int suggestionLimit);
    void viewStory(String storyId, String accountId);
    List<mediaservice.dtos.responses.UserAccountResponse> getStoryViewers(String storyId);
}

