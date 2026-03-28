package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryGroupResponse;
import mediaservice.dtos.responses.StoryReelItemResponse;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.mappers.StoryMapper;
import mediaservice.mappers.UserAccountMapper;
import mediaservice.models.StoryItem;
import mediaservice.models.Story;
import mediaservice.models.UserAccount;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RuleType;
import mediaservice.models.enums.StoryItemType;
import mediaservice.models.enums.VisibilityType;
import mediaservice.repositories.StoryItemRepository;
import mediaservice.repositories.StoryRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.StoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final StoryItemRepository storyItemRepository;
    private final StoryMapper storyMapper;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;

    @Override
    @Transactional
    public StoryResponse createStory(StoryRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }

        validateStoryItems(request);

        Story story = storyMapper.toEntity(request);

        UserAccount account = userAccountRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
        story.setAccount(account);

        if (story.getStatus() == null) {
            story.setStatus(ContentStatusType.ACTIVE);
        }

        if (story.getVisibility() == null) {
            story.setVisibility(VisibilityType.PUBLIC);
        }

        Set<StoryItem> pendingItems = story.getStoryItems() != null
            ? story.getStoryItems().stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
                : new HashSet<>();
        story.setStoryItems(new HashSet<>());

        Story savedStory = storyRepository.save(story);

        if (!pendingItems.isEmpty()) {
            pendingItems.forEach(item -> item.setStory(savedStory));
            List<StoryItem> persistedItems = storyItemRepository.saveAll(pendingItems);
            savedStory.setStoryItems(new HashSet<>(persistedItems));
        }

        return storyMapper.toResponse(savedStory);
    }

    private void validateStoryItems(StoryRequest request) {
        if (request.getStoryItems() == null || request.getStoryItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Each story must contain at least 1 item (TEXT_ITEM or IMAGE_ITEM)"
            );
        }

        boolean hasRequiredItem = request.getStoryItems().stream()
                .anyMatch(item -> item != null
                        && (item.getType() == StoryItemType.TEXT_ITEM || item.getType() == StoryItemType.IMAGE_ITEM));

        if (!hasRequiredItem) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Each story must contain at least 1 TEXT_ITEM or IMAGE_ITEM"
            );
        }
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
        List<Story> stories = storyRepository.findAllWithAccount();
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

        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            UserAccount account = userAccountRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            story.setAccount(account);
        }

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
        List<Story> stories = storyRepository.findByExpireAtAfter(LocalDateTime.now());
        return storyMapper.toResponseList(stories);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getAuthorizedActiveStories(String accountId) {
        List<Story> stories = storyRepository.findAuthorizedActiveStories(
                ContentStatusType.ACTIVE,
                VisibilityType.PUBLIC,
                VisibilityType.PRIVATE,
                VisibilityType.FRIENDS,
                RelationshipStatusType.ACCEPTED,
                VisibilityType.CUSTOM,
                RuleType.INCLUDE,
                RuleType.EXCLUDE,
                accountId,
                LocalDateTime.now()
        );
        return storyMapper.toResponseList(stories);
    }

    @Override
    @Transactional(readOnly = true)
    public StoryReelResponse getStoriesReel(String accountId, int suggestionLimit) {
        List<StoryResponse> stories = getAuthorizedActiveStories(accountId);

        if (!stories.isEmpty()) {
            return new StoryReelResponse(groupStoriesByAccount(stories), List.of());
        }

        int safeLimit = suggestionLimit <= 0 ? 8 : suggestionLimit;
        List<UserAccount> suggestedUsers = userAccountRepository.findSuggestedUsersForStoryReel(
                accountId,
                PageRequest.of(0, safeLimit)
        );

        return new StoryReelResponse(
                List.of(),
                userAccountMapper.toResponseList(suggestedUsers)
        );
    }

    private List<StoryGroupResponse> groupStoriesByAccount(List<StoryResponse> stories) {
        Map<String, List<StoryResponse>> grouped = new LinkedHashMap<>();

        for (StoryResponse story : stories) {
            String accountKey = story.getAccountId() != null ? story.getAccountId() : story.getId();
            grouped.computeIfAbsent(accountKey, ignored -> new ArrayList<>()).add(story);
        }

        List<StoryGroupResponse> groups = new ArrayList<>();
        for (List<StoryResponse> userStories : grouped.values()) {
            userStories.sort(
                Comparator.comparing(StoryResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed()
            );

            StoryResponse first = userStories.get(0);
            List<StoryReelItemResponse> reelItems = userStories.stream()
                    .map(this::toReelItem)
                    .toList();

            groups.add(new StoryGroupResponse(
                first.getAccountId(),
                first.getAccountUsername(),
                first.getAccountDisplayName(),
                first.getAccountAvatarUrl(),
                reelItems
            ));
        }
        return groups;
    }

    private StoryReelItemResponse toReelItem(StoryResponse story) {
        return new StoryReelItemResponse(
                story.getHighlightName(),
                story.getExpireAt(),
                story.getStoryItems(),
                story.getMusics(),
                story.getTotalViews(),
                story.getAccountAvatarUrl(),
                story.getCreatedAt(),
                story.getHashTags(),
                story.isHighlight(),
                story.getId(),
                story.getUpdatedAt(),
                story.getVisibility()
        );
    }
}

