package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.*;
import mediaservice.dtos.responses.StoryGroupResponse;
import mediaservice.dtos.responses.StoryReelItemResponse;
import mediaservice.dtos.responses.StoryReelResponse;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.mappers.ContentAccessControlMapper;
import mediaservice.mappers.StoryMapper;
import mediaservice.mappers.StoryItemMapper;
import mediaservice.mappers.UserAccountMapper;
import mediaservice.dtos.messages.MediaDeleteJob;
import mediaservice.realtime.MediaRealtimePublisher;
import mediaservice.realtime.MediaRealtimeUpdate;
import mediaservice.models.*;
import mediaservice.models.enums.*;
import mediaservice.repositories.StoryItemRepository;
import mediaservice.repositories.StoryRepository;
import mediaservice.repositories.StoryViewRepository;
import mediaservice.repositories.ContentViewHistoryRepository;
import mediaservice.repositories.RelationshipRepository;
import mediaservice.repositories.SavedContentRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.realtime.NotificationPublisher;
import mediaservice.services.MediaDeleteJobPublisher;
import mediaservice.services.MediaCompressionJobPublisher;
import mediaservice.services.MediaUploadJobPublisher;
import mediaservice.dtos.messages.MediaCompressionJob;
import mediaservice.dtos.messages.MediaUploadJob;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final StoryItemMapper storyItemMapper;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;
    private final StoryViewRepository storyViewRepository;
    private final ContentViewHistoryRepository contentViewHistoryRepository;
    private final SavedContentRepository savedContentRepository;
    private final MediaDeleteJobPublisher mediaDeleteJobPublisher;
    private final MediaUrlBuilder mediaUrlBuilder;
    private final MediaRealtimePublisher mediaRealtimePublisher;
    private final MediaCompressionJobPublisher mediaCompressionJobPublisher;
    private final MediaUploadJobPublisher mediaUploadJobPublisher;
    private final ContentAccessControlMapper accessControlMapper;
    private final RelationshipRepository relationshipRepository;
    private final NotificationPublisher notificationPublisher;

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

        // Process Access Controls
        processAccessControls(request, savedStory);
        Story finalStory = storyRepository.save(savedStory);

        publishAfterCommit(finalStory.getId(), "STORY", "CREATE");

        if (finalStory.getAccount() != null) {
            publishNotificationToFriendsAfterCommit(finalStory.getAccount().getId(), finalStory.getId(), "NEW_STORY", "Vừa thêm bảng tin mới");
        }

        return storyMapper.toResponse(finalStory);
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
    public StoryResponse updateStory(String id, StoryRequest request, List<MultipartFile> files, List<String> captions) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));

        // 1. Update basic fields using mapper
        storyMapper.updateEntity(request, story);

        List<String> previousKeys = collectStoryMediaKeys(story);
        Set<String> keepKeys = new HashSet<>();

        // 2. Ensure account is correct
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            UserAccount account = userAccountRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            story.setAccount(account);
        }

        // 3. Process Story Items
        Set<StoryItem> currentItems = story.getStoryItems() != null ? story.getStoryItems() : new HashSet<>();
        Set<StoryItem> nextItems = new LinkedHashSet<>();
        
        int filePointer = 0;
        boolean hasUpdateAsyncJobs = false;

        if (request.getStoryItems() != null) {
            for (int i = 0; i < request.getStoryItems().size(); i++) {
                StoryItemRequest itemReq = request.getStoryItems().get(i);
                if (itemReq == null) continue;

                // Match by ID first if provided
                StoryItem matched = null;
                if (itemReq.getId() != null) {
                    matched = currentItems.stream()
                            .filter(it -> itemReq.getId().equals(it.getId()))
                            .findFirst()
                            .orElse(null);
                }

                // If not matched by ID, try matching by URL for media items or content/pos for text items
                if (matched == null) {
                    if (itemReq.getType() != StoryItemType.TEXT_ITEM) {
                        String url = (itemReq.getType() == StoryItemType.IMAGE_ITEM && itemReq.getImageItem() != null)
                                ? itemReq.getImageItem().getUrl()
                                : (itemReq.getType() == StoryItemType.VIDEO_ITEM && itemReq.getVideoItem() != null)
                                    ? itemReq.getVideoItem().getUrl()
                                    : null;

                        if (url != null) {
                            String relPath = resolveKey(url, "stories");
                            matched = currentItems.stream()
                                    .filter(it -> {
                                        if (it instanceof ImageItem ii) return relPath.equals(resolveKey(ii.getUrl(), "stories"));
                                        if (it instanceof VideoItem vi) return relPath.equals(resolveKey(vi.getUrl(), "stories"));
                                        return false;
                                    })
                                    .findFirst()
                                    .orElse(null);
                            if (matched != null) keepKeys.add(relPath);
                        }
                    } else if (itemReq.getTextItem() != null) {
                        // For text items, match by content and position if ID is missing
                        String content = itemReq.getTextItem().getContent();
                        matched = currentItems.stream()
                                .filter(it -> it instanceof TextItem ti && 
                                             Objects.equals(ti.getContent(), content) &&
                                             Math.abs(ti.getPositionX() - itemReq.getPositionX()) < 0.01 &&
                                             Math.abs(ti.getPositionY() - itemReq.getPositionY()) < 0.01)
                                .findFirst()
                                .orElse(null);
                    }
                }

                if (matched != null) {
                    // Update existing item's spatial properties
                    matched.setZIndex(itemReq.getZIndex());
                    matched.setPositionX(itemReq.getPositionX());
                    matched.setPositionY(itemReq.getPositionY());
                    matched.setRotation(itemReq.getRotation());
                    matched.setScale(itemReq.getScale());
                    
                    if (matched instanceof TextItem ti && itemReq.getTextItem() != null) {
                        ti.setContent(itemReq.getTextItem().getContent());
                        ti.setBackgroundColor(itemReq.getTextItem().getBackgroundColor());
                    }
                    
                    nextItems.add(matched);
                } else {
                    // New item or item with file upload
                    String url = (itemReq.getType() == StoryItemType.IMAGE_ITEM && itemReq.getImageItem() != null)
                            ? itemReq.getImageItem().getUrl()
                            : (itemReq.getType() == StoryItemType.VIDEO_ITEM && itemReq.getVideoItem() != null)
                                ? itemReq.getVideoItem().getUrl()
                                : null;

                    if (url != null && mediaUrlBuilder.isFullUrl(url) && !mediaUrlBuilder.isInternalS3Url(url)) {
                        // This is likely a placeholder or external URL that needs a file from 'files'
                        if (files != null && filePointer < files.size()) {
                            MultipartFile file = files.get(filePointer++);
                            String s3Key = buildS3Key("stories", file.getOriginalFilename());
                            keepKeys.add(s3Key);

                            StoryItem newItem = storyItemMapper.toEntity(itemReq);
                            if (newItem instanceof ImageItem ii) ii.setUrl(s3Key);
                            else if (newItem instanceof VideoItem vi) vi.setUrl(s3Key);
                            
                            newItem.setStory(story);
                            storyItemRepository.save(newItem);
                            enqueueAsyncMediaProcessing(file, s3Key, newItem.getId());
                            nextItems.add(newItem);
                            hasUpdateAsyncJobs = true;
                        }
                    } else {
                        // Already uploaded or text item
                        StoryItem newItem = storyItemMapper.toEntity(itemReq);
                        newItem.setStory(story);
                        if (url != null) keepKeys.add(resolveKey(url, "stories"));
                        nextItems.add(newItem);
                    }
                }
            }
        }

        // 4. Update the collection in-place to keep JPA happy and preserve identities
        if (story.getStoryItems() == null) {
            story.setStoryItems(nextItems);
        } else {
            // Remove items that are no longer present
            story.getStoryItems().removeIf(it -> !nextItems.contains(it));
            // Add new items
            story.getStoryItems().addAll(nextItems);
        }

        // 4b. Process Access Controls
        processAccessControls(request, story);

        Story updatedStory = storyRepository.save(story);

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

        if (updatedStory.getAccount() != null) {
            publishNotificationToFriendsAfterCommit(updatedStory.getAccount().getId(), updatedStory.getId(), "UPDATE_STORY", "Vừa cập nhật bảng tin");
        }

        return storyMapper.toResponse(updatedStory);
    }

    private void enqueueAsyncMediaProcessing(MultipartFile file, String s3Key, String storyItemId) {
        // Reuse logic from StoryController (I should probably move this to a shared helper, but for now I'll duplicate as it's small)
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        boolean isVideo = contentType.startsWith("video/");
        boolean isAudio = contentType.startsWith("audio/");

        try {
            if (isVideo || isAudio) {
                String mediaType = isAudio ? "AUDIO" : "VIDEO";
                String outputContentType = isAudio ? "audio/mp4" : "video/mp4";
                String prefix = isAudio ? "audio-" : "video-";

                java.nio.file.Path tempPath = mediaservice.utils.MediaTempFileStore.saveToTemp(file, prefix);
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
                // I need the publisher here. They are already in the class.
                mediaCompressionJobPublisher.publish(job);
                return;
            }

            java.nio.file.Path tempPath = mediaservice.utils.MediaTempFileStore.saveToTemp(file, "image-");
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
            // Ignore enqueue failures.
        }
    }
    

    @Override
    @Transactional
    @CacheEvict(value = {"stories", "allStories", "storyReel"}, allEntries = true)
    public void deleteStory(String id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));

        List<String> deleteKeys = collectStoryMediaKeys(story);
        
        // Delete related data first
        storyViewRepository.deleteByStoryId(id);
        contentViewHistoryRepository.deleteByContentId(story.getId());
        savedContentRepository.deleteByContentId(story.getId());
        
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

    private void publishNotificationToFriendsAfterCommit(String authorId, String storyId, String type, String message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            notifyFriends(authorId, storyId, type, message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyFriends(authorId, storyId, type, message);
            }
        });
    }

    private void notifyFriends(String authorId, String storyId, String type, String message) {
        if (authorId == null || storyId == null) return;
        List<mediaservice.models.Relationship> friends = relationshipRepository.findFriendsByUserId(authorId, RelationshipStatusType.ACCEPTED);
        for (mediaservice.models.Relationship r : friends) {
            if (r.getRequester() != null && r.getReceiver() != null) {
                String friendId = r.getRequester().getId().equals(authorId) ? r.getReceiver().getId() : r.getRequester().getId();
                notificationPublisher.publishNotification(friendId, authorId, type, message, storyId);
            }
        }
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

    private String buildS3Key(String folder, String originalFilename) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now());
        String cleanName = (originalFilename != null) ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_") : "file";
        return folder + "/" + timestamp + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "_" + cleanName;
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
        List<Story> stories = storyRepository.findAll().stream()
                .filter(s -> s.getAccount() != null && s.getAccount().getId().equals(userId))
                .sorted(Comparator.comparing(Story::getCreatedAt).reversed())
                .toList();
        return stories.stream().map(this::toFullResponse).toList();
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
        return stories.stream().map(this::toFullResponse).toList();
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

    private StoryResponse toFullResponse(Story story) {
        StoryResponse response = storyMapper.toResponse(story);
        response.setTotalViews(storyViewRepository.countByStoryId(story.getId()));
        return response;
    }

    @Override
    @Transactional
    public void viewStory(String storyId, String accountId) {
        if (storyViewRepository.existsByStoryIdAndAccountId(storyId, accountId)) {
            return;
        }

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        
        UserAccount account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        mediaservice.models.StoryView view = new mediaservice.models.StoryView();
        view.setStory(story);
        view.setAccount(account);
        storyViewRepository.save(view);
    }

    @Override
    public List<mediaservice.dtos.responses.UserAccountResponse> getStoryViewers(String storyId) {
        return storyViewRepository.findByStoryIdOrderByViewedAtDesc(storyId).stream()
                .map(view -> userAccountMapper.toResponse(view.getAccount()))
                .toList();
    }

    private void processAccessControls(StoryRequest request, Story story) {
        if (request.getVisibility() != VisibilityType.CUSTOM) {
            if (story.getAccessControls() != null) {
                story.getAccessControls().clear();
            }
            return;
        }

        if (request.getAccessControls() == null || request.getAccessControls().isEmpty()) {
            return;
        }

        Set<ContentAccessControl> accessControls = new HashSet<>();
        for (AccessControlRequest acReq : request.getAccessControls()) {
            UserAccount acAccount = userAccountRepository.findById(acReq.getAccountId()).orElse(null);
            if (acAccount != null) {
                ContentAccessControl ac = accessControlMapper.toEntity(acReq);
                ac.setAccount(acAccount);
                ac.setContent(story);
                accessControls.add(ac);
            }
        }

        if (story.getAccessControls() == null) {
            story.setAccessControls(accessControls);
        } else {
            story.getAccessControls().clear();
            story.getAccessControls().addAll(accessControls);
        }
    }
}
