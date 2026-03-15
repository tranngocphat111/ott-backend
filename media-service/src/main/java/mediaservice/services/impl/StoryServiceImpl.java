package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.mappers.StoryMapper;
import mediaservice.models.Story;
import mediaservice.repositories.StoryRepository;
import mediaservice.services.StoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final StoryMapper storyMapper;

    @Override
    @Transactional
    public StoryResponse createStory(StoryRequest request) {
        Story story = storyMapper.toEntity(request);
        Story savedStory = storyRepository.save(story);
        return storyMapper.toResponse(savedStory);
    }

    @Override
    @Transactional(readOnly = true)
    public StoryResponse getStoryById(String id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));
        return storyMapper.toResponse(story);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getAllStories() {
        List<Story> stories = storyRepository.findAll();
        return storyMapper.toResponseList(stories);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StoryResponse> getAllStories(Pageable pageable) {
        Page<Story> stories = storyRepository.findAll(pageable);
        return stories.map(storyMapper::toResponse);
    }

    @Override
    @Transactional
    public StoryResponse updateStory(String id, StoryRequest request) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));
        storyMapper.updateEntity(request, story);
        Story updatedStory = storyRepository.save(story);
        return storyMapper.toResponse(updatedStory);
    }

    @Override
    @Transactional
    public void deleteStory(String id) {
        if (!storyRepository.existsById(id)) {
            throw new RuntimeException("Story not found with id: " + id);
        }
        storyRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getStoriesByUserId(String userId) {
        List<Story> stories = storyRepository.findAll(); // TODO: Add custom query
        return storyMapper.toResponseList(stories);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getActiveStories() {
        List<Story> stories = storyRepository.findAll(); // TODO: Filter by expireAt > now
        return storyMapper.toResponseList(stories);
    }
}

