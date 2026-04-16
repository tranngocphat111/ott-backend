package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.requests.StoryItemRequest;
import mediaservice.dtos.responses.StoryGroupResponse;
import mediaservice.dtos.responses.StoryReelItemResponse;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.mappers.StoryMapper;
import mediaservice.mappers.UserAccountMapper;
import mediaservice.dtos.messages.MediaDeleteJob;
import mediaservice.realtime.MediaRealtimePublisher;
import mediaservice.realtime.MediaRealtimeUpdate;
import mediaservice.models.ImageItem;
import mediaservice.models.StoryItem;
import mediaservice.models.Story;
import mediaservice.models.UserAccount;
import mediaservice.models.VideoItem;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RuleType;
import mediaservice.models.enums.StoryItemType;
import mediaservice.models.enums.VisibilityType;
import mediaservice.repositories.StoryItemRepository;
import mediaservice.repositories.StoryRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.MediaDeleteJobPublisher;
import mediaservice.services.StoryService;
import mediaservice.utils.MediaUrlBuilder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final MediaDeleteJobPublisher mediaDeleteJobPublisher;
    private final MediaUrlBuilder mediaUrlBuilder;
    private final MediaRealtimePublisher mediaRealtimePublisher;

    @Override
    @Transactional
    @CacheEvict(value = {"stories", "allStories", "storyReel"}, allEntries = true)
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

        boolean hasMedia = story.getStoryItems() != null && story.getStoryItems().stream().anyMatch(item ->
            item instanceof ImageItem || item instanceof VideoItem
        );
        if (!hasMedia) {
            publishAfterCommit(savedStory.getId(), "STORY", "CREATE");
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
                && (item.getType() == StoryItemType.TEXT_ITEM
                || item.getType() == StoryItemType.IMAGE_ITEM
                || item.getType() == StoryItemType.VIDEO_ITEM));

        if (!hasRequiredItem) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Each story must contain at least 1 TEXT_ITEM, IMAGE_ITEM, or VIDEO_ITEM"
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
    @CacheEvict(value = {"stories", "allStories", "storyReel"}, allEntries = true)
    public StoryResponse updateStory(String id, StoryRequest request) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));

        List<String> previousKeys = collectStoryMediaKeys(story);
        storyMapper.updateEntity(request, story);

        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            UserAccount account = userAccountRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            story.setAccount(account);
        }

        Story updatedStory = storyRepository.save(story);

        if (request.getStoryItems() != null) {
            List<String> keepKeys = collectStoryRequestMediaKeys(request.getStoryItems());
            List<String> deleteKeys = new ArrayList<>();
            for (String key : previousKeys) {
                if (!keepKeys.contains(key)) {
                    deleteKeys.add(key);
                }
            }
            if (deleteKeys.isEmpty()) {
                publishAfterCommit(story.getId(), "STORY", "UPDATE");
            } else {
                enqueueDeleteJob(deleteKeys, story.getId(), "STORY", "UPDATE");
            }
        } else {
            publishAfterCommit(story.getId(), "STORY", "UPDATE");
        }

        return storyMapper.toResponse(updatedStory);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"stories", "allStories", "storyReel"}, allEntries = true)
    public void deleteStory(String id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));

        List<String> deleteKeys = collectStoryMediaKeys(story);
        storyRepository.delete(story);

        if (deleteKeys.isEmpty()) {
            publishAfterCommit(story.getId(), "STORY", "DELETE");
        } else {
            enqueueDeleteJob(deleteKeys, story.getId(), "STORY", "DELETE");
        }
    }

    private List<String> collectStoryMediaKeys(Story story) {
        if (story.getStoryItems() == null || story.getStoryItems().isEmpty()) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        for (StoryItem item : story.getStoryItems()) {
            if (item instanceof ImageItem imageItem) {
                addKey(keys, resolveKey(imageItem.getUrl(), "stories"));
            } else if (item instanceof VideoItem videoItem) {
                addKey(keys, resolveKey(videoItem.getUrl(), "stories"));
                addKey(keys, resolveKey(videoItem.getThumbnailUrl(), "stories"));
            }
        }

        return keys;
    }

    private void enqueueDeleteJob(List<String> keys, String contentId, String contentTargetType, String operation) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        try {
            mediaDeleteJobPublisher.publish(new MediaDeleteJob(keys, contentId, contentTargetType, operation));
        } catch (Exception ex) {
            // Ignore delete job failures to keep delete flow responsive.
        }
    }

    private void publishAfterCommit(String contentId, String contentTargetType, String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            mediaRealtimePublisher.publish(contentTargetType, contentId, operation, List.of(), List.of());
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mediaRealtimePublisher.publish(contentTargetType, contentId, operation, List.of(), List.of());
            }
        });
    }

    private void addKey(List<String> keys, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        keys.add(key);
    }

    private List<String> collectStoryRequestMediaKeys(List<StoryItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        for (StoryItemRequest item : items) {
            if (item == null || item.getType() == null) {
                continue;
            }
            switch (item.getType()) {
                case IMAGE_ITEM -> {
                    if (item.getImageItem() != null) {
                        addKey(keys, resolveKey(item.getImageItem().getUrl(), "stories"));
                    }
                }
                case VIDEO_ITEM -> {
                    if (item.getVideoItem() != null) {
                        addKey(keys, resolveKey(item.getVideoItem().getUrl(), "stories"));
                        addKey(keys, resolveKey(item.getVideoItem().getThumbnailUrl(), "stories"));
                    }
                }
                case TEXT_ITEM -> {
                    // No media to keep.
                }
            }
        }

        return keys;
    }

    private String resolveKey(String raw, String defaultFolder) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = mediaUrlBuilder.isFullUrl(raw)
                ? mediaUrlBuilder.extractRelativePath(raw)
                : raw.trim();

        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        if (normalized.contains("/")) {
            return normalized;
        }

        return defaultFolder + "/" + normalized;
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
        List<StoryGroupResponse> storyGroups = groupStoriesByAccount(stories);

        if (storyGroups.size() >= 5) {
            return new StoryReelResponse(storyGroups, List.of());
        }

        int safeLimit = suggestionLimit <= 0 ? 8 : suggestionLimit;
        int fillCount = Math.max(0, 5 - storyGroups.size());
        int limit = Math.min(safeLimit, fillCount);
        if (limit == 0) {
            return new StoryReelResponse(storyGroups, List.of());
        }
        List<UserAccount> suggestedUsers = userAccountRepository.findSuggestedUsersForStoryReel(
                accountId,
                PageRequest.of(0, limit)
        );

        return new StoryReelResponse(
                storyGroups,
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

