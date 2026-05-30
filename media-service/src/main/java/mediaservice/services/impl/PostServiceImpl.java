package mediaservice.services.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import mediaservice.dtos.messages.MediaCompressionJob;
import mediaservice.dtos.messages.MediaDeleteJob;
import mediaservice.dtos.messages.MediaUploadJob;
import mediaservice.dtos.requests.AccessControlRequest;
import mediaservice.dtos.requests.MediaRequest;
import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.mappers.PostMapper;
import mediaservice.models.Content;
import mediaservice.models.ContentAccessControl;
import mediaservice.models.ImageMedia;
import mediaservice.models.Post;
import mediaservice.models.UserAccount;
import mediaservice.models.VideoMedia;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.MediaType;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RuleType;
import mediaservice.models.enums.VisibilityType;
import mediaservice.realtime.MediaRealtimePublisher;
import mediaservice.realtime.MediaRealtimeUpdate;
import mediaservice.realtime.PostActivityPublisher;
import mediaservice.repositories.CommentRepository;
import mediaservice.repositories.ContentAccessControlRepository;
import mediaservice.repositories.ContentViewHistoryRepository;
import mediaservice.repositories.MediaRepository;
import mediaservice.repositories.PostRepository;
import mediaservice.repositories.ReactionRepository;
import mediaservice.repositories.RelationshipRepository;
import mediaservice.repositories.SavedContentRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.AnalyticsEventPublisher;
import mediaservice.services.MediaCompressionJobPublisher;
import mediaservice.services.MediaDeleteJobPublisher;
import mediaservice.services.MediaUploadJobPublisher;
import mediaservice.services.PostService;
import mediaservice.services.S3Service;
import mediaservice.utils.MediaTempFileStore;
import mediaservice.utils.MediaUrlBuilder;
import mediaservice.utils.TextTagParser;
import mediaservice.models.HashTag;
import mediaservice.repositories.HashTagRepository;
import mediaservice.realtime.NotificationPublisher;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final RelationshipRepository relationshipRepository;
    private final UserAccountRepository userAccountRepository;
    private final MediaRepository mediaRepository;
    private final S3Service s3Service;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final ContentAccessControlRepository contentAccessControlRepository;
    private final MediaUrlBuilder mediaUrlBuilder;
    private final MediaCompressionJobPublisher mediaCompressionJobPublisher;
    private final MediaDeleteJobPublisher mediaDeleteJobPublisher;
    private final MediaUploadJobPublisher mediaUploadJobPublisher;
    private final MediaRealtimePublisher mediaRealtimePublisher;
    private final PostActivityPublisher postActivityPublisher;
    private final ContentViewHistoryRepository contentViewHistoryRepository;
    private final SavedContentRepository savedContentRepository;
    private final HashTagRepository hashTagRepository;

    private final mediaservice.services.UserSyncService userSyncService;
    private final mediaservice.mappers.ContentAccessControlMapper contentAccessControlMapper;
    private final NotificationPublisher notificationPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    /* ─── helper: ensure author info is fresh ─ */
    private UserAccount ensureUserSynced(String userId) {
        return userSyncService.syncUser(userId)
                .orElseGet(() -> userAccountRepository.findById(userId).orElse(null));
    }

    /* ─── helper: fill totalReactions / totalComments from DB ─ */
    private PostResponse enrichCounts(PostResponse response, String postId) {
        return enrichCounts(response, postId, null);
    }

    private PostResponse enrichCounts(PostResponse response, String postId, String viewerAccountId) {
        if (response == null) return null;
        response.setTotalReactions(
                (int) reactionRepository.countByTargetIdAndTargetType(postId, ReactionTargetType.POST));
        response.setTotalComments(
                (int) commentRepository.countByContent_IdAndIsDeletedFalse(postId));
        response.setTotalShares(
                (int) postRepository.countBySharedPost_Id(postId));
        
        if (response.getSharedPost() != null) {
            Post originalPost = postRepository.findById(response.getSharedPost().getId()).orElse(null);
            if (originalPost == null || originalPost.getStatus() == ContentStatusType.DELETED) {
                response.setSharedPostDeleted(true);
                response.setSharedPost(null);
            } else if (!canUserViewPost(originalPost, viewerAccountId)) {
                response.setSharedPostRestricted(true);
                response.setSharedPost(null);
            } else {
                enrichCounts(response.getSharedPost(), originalPost.getId(), viewerAccountId);
            }
        }
        return response;
    }

    private boolean canUserViewPost(Post post, String viewerAccountId) {
        if (post == null) return false;
        if (post.getStatus() == ContentStatusType.DELETED) return false;

        String authorId = post.getAccount() != null ? post.getAccount().getId() : null;
        if (authorId == null) return false;

        // If viewer is anonymous/null, only public posts are visible
        if (viewerAccountId == null || viewerAccountId.isBlank()) {
            return post.getVisibility() == VisibilityType.PUBLIC;
        }

        // 1. Check if user is blocked
        boolean isBlocked = relationshipRepository.existsBlockBetween(authorId, viewerAccountId);
        if (isBlocked) return false;

        // 2. Author can always see their own posts
        if (authorId.equals(viewerAccountId)) return true;

        // 3. Check visibility rules
        if (post.getVisibility() == VisibilityType.PUBLIC) {
            return true;
        }

        if (post.getVisibility() == VisibilityType.PRIVATE) {
            return authorId.equals(viewerAccountId);
        }

        if (post.getVisibility() == VisibilityType.FRIENDS) {
            return relationshipRepository.isFriend(authorId, viewerAccountId);
        }

        if (post.getVisibility() == VisibilityType.CUSTOM) {
            List<ContentAccessControl> controls = contentAccessControlRepository.findByContent(post);
            if (controls == null || controls.isEmpty()) {
                return false;
            }

            boolean hasIncludeRules = controls.stream().anyMatch(c -> c.getRuleType() == RuleType.INCLUDE);
            if (hasIncludeRules) {
                return controls.stream()
                        .anyMatch(c -> c.getRuleType() == RuleType.INCLUDE && 
                                       c.getAccount() != null && 
                                       c.getAccount().getId().equals(viewerAccountId));
            }

            return controls.stream()
                    .noneMatch(c -> c.getRuleType() == RuleType.EXCLUDE && 
                                    c.getAccount() != null && 
                                    c.getAccount().getId().equals(viewerAccountId));
        }

        return false;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allPosts", "userPosts"}, allEntries = true)
    public PostResponse createPost(PostRequest request) {
        Post post = postMapper.toEntity(request);
        
        // ⭐ Set default status if null
        if (post.getStatus() == null) {
            post.setStatus(ContentStatusType.ACTIVE);
        }
        
        Post savedPost = postRepository.save(post);
        // Process hashtags and @mentions in caption
        processTagsAndMentions(savedPost);
        String userId = savedPost.getAccount() != null ? savedPost.getAccount().getId() : null;
        publishPostCreatedAnalyticsAfterCommit(savedPost.getId(), userId);
        publishAfterCommit(savedPost.getId(), "POST", "CREATE");
        return enrichCounts(postMapper.toResponse(savedPost), savedPost.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allPosts", "userPosts"}, allEntries = true)
    public PostResponse createPost(String accountId, String caption,
                                   VisibilityType visibility,
                                   List<MultipartFile> files,
                                   List<String> captions,
                                   List<AccessControlRequest> accessControls) {
        // 1. Resolve author
        UserAccount account = ensureUserSynced(accountId);
        if (account == null) {
            throw new RuntimeException("User not found or sync failed for id: " + accountId);
        }

        // 2. Persist the Post
        Post post = new Post();
        post.setAccount(account);
        post.setCaption(caption);
        post.setVisibility(visibility != null ? visibility : VisibilityType.PUBLIC);
        
        // ⭐ Set default status to ACTIVE
        post.setStatus(ContentStatusType.ACTIVE);
        
        Post savedPost = postRepository.save(post);

        if (visibility == VisibilityType.CUSTOM && accessControls != null && !accessControls.isEmpty()) {
            List<ContentAccessControl> controls = new java.util.ArrayList<>();
            for (AccessControlRequest req : accessControls) {
                if (req == null || req.getAccountId() == null || req.getRuleType() == null) continue;
                if (req.getAccountId().equals(accountId)) continue;
                UserAccount target = ensureUserSynced(req.getAccountId());
                if (target == null) continue;
                ContentAccessControl control = new ContentAccessControl();
                control.setAccount(target);
                control.setContent(savedPost);
                control.setRuleType(req.getRuleType());
                controls.add(control);
            }
            if (!controls.isEmpty()) {
                contentAccessControlRepository.saveAll(controls);
                savedPost.setAccessControls(new java.util.HashSet<>(controls));
            }
        }

        // 3. Save media rows first, then enqueue async upload/compress jobs.
        boolean hasAsyncJobs = false;
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file == null || file.isEmpty()) continue;

                String mediaCaption = (captions != null && i < captions.size())
                        ? (captions.get(i).isBlank() ? null : captions.get(i)) : null;
                int orderIndex = i;

                String contentType = file.getContentType() != null ? file.getContentType() : "";
                boolean isVideo = contentType.startsWith("video/");
                String folder = isVideo ? "social/videos" : "social/posts";
                String s3Key = buildS3Key(folder, file.getOriginalFilename());
                String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);

                if (isVideo) {
                    VideoMedia media = new VideoMedia();
                    media.setUrl(s3Key);
                    media.setOrderIndex(orderIndex);
                    media.setCaption(mediaCaption);
                    media.setContent(savedPost);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, savedPost.getId(), "POST", "CREATE", media.getId(), orderIndex);
                    hasAsyncJobs = true;
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(s3Key);
                    media.setOrderIndex(orderIndex);
                    media.setCaption(mediaCaption);
                    media.setContent(savedPost);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, savedPost.getId(), "POST", "CREATE", media.getId(), orderIndex);
                    hasAsyncJobs = true;
                }
            }
        }

        // 4. Flush và refresh để lấy medias list mới nhất từ DB vào entity
        //    (tránh trả về Hibernate cache cũ không có media)
        entityManager.flush();
        entityManager.refresh(savedPost);

        publishPostCreatedAnalyticsAfterCommit(savedPost.getId(), accountId);
        if (!hasAsyncJobs) {
            publishAfterCommit(savedPost.getId(), "POST", "CREATE");
        }

        // Process hashtags and @mentions in caption (after media saved and refresh)
        processTagsAndMentions(savedPost);

        return enrichCounts(postMapper.toResponse(savedPost), savedPost.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "posts", key = "#id", unless = "#result == null")
    public PostResponse getPostById(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        return enrichCounts(postMapper.toResponse(post), post.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(String id, String viewerId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        return enrichCounts(postMapper.toResponse(post), post.getId(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getAllPosts() {
        return postRepository.findAll().stream()
                .map(p -> enrichCounts(postMapper.toResponse(p), p.getId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getAllPosts(Pageable pageable) {
        return postRepository.findallPosts(pageable)
                .map(p -> enrichCounts(postMapper.toResponse(p), p.getId()));
    }


    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> findAllPostsWithAuthorized(Pageable pageable, String accountId) {
        return postRepository.
                findAllPostsWithAuthorized(
                        ContentStatusType.ACTIVE,
                        VisibilityType.PUBLIC,
                        VisibilityType.PRIVATE,
                        VisibilityType.FRIENDS,
                        RelationshipStatusType.ACCEPTED,
                        VisibilityType.CUSTOM,
                        RuleType.INCLUDE,
                        RuleType.EXCLUDE,
                        accountId,
                        pageable
                ).map(p -> enrichCounts(postMapper.toResponse(p), p.getId(), accountId));
    }


    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "userPosts"}, allEntries = true)
    public PostResponse updatePost(String id, PostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        postMapper.updateEntity(request, post);
        Post updatedPost = postRepository.save(post);
        publishAfterCommit(updatedPost.getId(), "POST", "UPDATE");
        return enrichCounts(postMapper.toResponse(updatedPost), updatedPost.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "userPosts"}, allEntries = true)
    public PostResponse updatePost(String id, String accountId, String caption,
                                   VisibilityType visibility,
                                   List<MultipartFile> files,
                                   List<String> captions,
                                   List<AccessControlRequest> accessControls,
                                   List<MediaRequest> existingMedias) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        List<String> previousKeys = collectPostMediaKeys(post);
        java.util.Set<String> keepKeys = new java.util.HashSet<>();

        String ownerId = accountId != null ? accountId :
            (post.getAccount() != null ? post.getAccount().getId() : null);

        if (caption != null) {
            post.setCaption(caption);
        }
        if (visibility != null) {
            post.setVisibility(visibility);
        }
        if (post.getStatus() == null) {
            post.setStatus(ContentStatusType.ACTIVE);
        }

        // Update access controls (mutate managed collection to keep orphanRemoval happy)
        java.util.Set<ContentAccessControl> controls = post.getAccessControls();
        if (controls == null) {
            controls = new java.util.HashSet<>();
            post.setAccessControls(controls);
        }
        controls.clear();

        if (visibility == VisibilityType.CUSTOM && accessControls != null && !accessControls.isEmpty()) {
            for (AccessControlRequest req : accessControls) {
                if (req == null || req.getAccountId() == null || req.getRuleType() == null) continue;
                if (ownerId != null && req.getAccountId().equals(ownerId)) continue;
                UserAccount target = ensureUserSynced(req.getAccountId());
                if (target == null) continue;
                ContentAccessControl control = new ContentAccessControl();
                control.setAccount(target);
                control.setContent(post);
                control.setRuleType(req.getRuleType());
                controls.add(control);
            }
        }

        // 3. Process Media items in the unified list (preserving order)
        List<mediaservice.models.Media> currentMedias = post.getMedias() != null ? new ArrayList<>(post.getMedias()) : new ArrayList<>();
        List<mediaservice.models.Media> nextMedias = new ArrayList<>();
        
        int filePointer = 0;
        boolean hasUpdateAsyncJobs = false;

        if (existingMedias != null && !existingMedias.isEmpty()) {
            for (int i = 0; i < existingMedias.size(); i++) {
                MediaRequest req = existingMedias.get(i);
                if (req == null || req.getUrl() == null) continue;

                boolean isInternal = mediaUrlBuilder.isInternalS3Url(req.getUrl());
                int targetOrderIndex = i; // Use list position as orderIndex

                if (isInternal) {
                    // --- CASE A: Existing S3 Media ---
                    String reqRelativePath = resolveKey(req.getUrl(), req.getType() == MediaType.VIDEO_MEDIA ? "social/videos" : "social/posts");
                    addKey(keepKeys, reqRelativePath);
                    if (req.getThumbnailUrl() != null) {
                        addKey(keepKeys, resolveKey(req.getThumbnailUrl(), "social/videos"));
                    }

                    // Try to find matching existing media in DB
                    mediaservice.models.Media matched = currentMedias.stream()
                            .filter(m -> {
                                String mRelative = resolveKey(m.getUrl(), m instanceof VideoMedia ? "social/videos" : "social/posts");
                                return mRelative != null && mRelative.equals(reqRelativePath);
                            })
                            .findFirst()
                            .orElse(null);

                    if (matched != null) {
                        log.info("[updatePost] Keeping existing media: {} at index {}", req.getUrl(), targetOrderIndex);
                        matched.setOrderIndex(targetOrderIndex);
                        matched.setCaption(req.getCaption());
                        if (matched instanceof VideoMedia vm && req.getThumbnailUrl() != null) {
                            vm.setThumbnailUrl(mediaUrlBuilder.extractFileName(req.getThumbnailUrl()));
                        }
                        nextMedias.add(matched);
                    } else {
                        log.info("[updatePost] URL is S3 but no DB record found (pasted from elsewhere?): {}. Creating new record.", req.getUrl());
                        if (req.getType() == MediaType.VIDEO_MEDIA) {
                            VideoMedia media = new VideoMedia();
                            media.setUrl(reqRelativePath);
                            media.setOrderIndex(targetOrderIndex);
                            media.setCaption(req.getCaption());
                            media.setContent(post);
                            if (req.getThumbnailUrl() != null) {
                                media.setThumbnailUrl(mediaUrlBuilder.extractFileName(req.getThumbnailUrl()));
                            }
                            nextMedias.add(media);
                        } else {
                            ImageMedia media = new ImageMedia();
                            media.setUrl(reqRelativePath);
                            media.setOrderIndex(targetOrderIndex);
                            media.setCaption(req.getCaption());
                            media.setContent(post);
                            nextMedias.add(media);
                        }
                    }
                } else {
                    // --- CASE B: New Media (Placeholder) ---
                    if (files != null && filePointer < files.size()) {
                        MultipartFile file = files.get(filePointer++);
                        if (file.isEmpty()) continue;

                        String contentType = file.getContentType() != null ? file.getContentType() : "";
                        boolean isVideo = contentType.startsWith("video/");
                        String folder = isVideo ? "social/videos" : "social/posts";

                        String s3Key = buildS3Key(folder, file.getOriginalFilename());
                        log.info("[updatePost] Processing placeholder #{} as new file -> {}", targetOrderIndex, s3Key);
                        addKey(keepKeys, s3Key);

                        if (isVideo) {
                            VideoMedia media = new VideoMedia();
                            media.setUrl(s3Key);
                            media.setOrderIndex(targetOrderIndex);
                            media.setCaption(req.getCaption());
                            media.setContent(post);
                            nextMedias.add(media);
                            mediaRepository.save(media);
                            enqueueAsyncMediaProcessing(file, s3Key, post.getId(), "POST", "UPDATE", media.getId(), targetOrderIndex);
                        } else {
                            ImageMedia media = new ImageMedia();
                            media.setUrl(s3Key);
                            media.setOrderIndex(targetOrderIndex);
                            media.setCaption(req.getCaption());
                            media.setContent(post);
                            nextMedias.add(media);
                            mediaRepository.save(media);
                            enqueueAsyncMediaProcessing(file, s3Key, post.getId(), "POST", "UPDATE", media.getId(), targetOrderIndex);
                        }
                        hasUpdateAsyncJobs = true;
                    } else {
                        log.warn("[updatePost] Placeholder URL '{}' at index {} but no file available in 'files' list!", req.getUrl(), targetOrderIndex);
                    }
                }
            }
        }

        // Add any leftover files that weren't matched by placeholders (fallback)
        if (files != null && filePointer < files.size()) {
            int currentMaxOrder = nextMedias.stream().mapToInt(mediaservice.models.Media::getOrderIndex).max().orElse(-1);
            for (int i = filePointer; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file.isEmpty()) continue;
                
                int targetOrderIndex = ++currentMaxOrder;
                String contentType = file.getContentType() != null ? file.getContentType() : "";
                boolean isVideo = contentType.startsWith("video/");
                String folder = isVideo ? "social/videos" : "social/posts";
                String s3Key = buildS3Key(folder, file.getOriginalFilename());
                
                log.info("[updatePost] Appending leftover file -> {} at index {}", s3Key, targetOrderIndex);
                addKey(keepKeys, s3Key);

                if (isVideo) {
                    VideoMedia media = new VideoMedia();
                    media.setUrl(s3Key);
                    media.setOrderIndex(targetOrderIndex);
                    media.setContent(post);
                    nextMedias.add(media);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, post.getId(), "POST", "UPDATE", media.getId(), targetOrderIndex);
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(s3Key);
                    media.setOrderIndex(targetOrderIndex);
                    media.setContent(post);
                    nextMedias.add(media);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, post.getId(), "POST", "UPDATE", media.getId(), targetOrderIndex);
                }
                hasUpdateAsyncJobs = true;
            }
        }

        // Update post medias collection
        post.getMedias().clear();
        post.getMedias().addAll(nextMedias);

        Post updatedPost = postRepository.save(post);
        entityManager.flush();

        log.info("[updatePost] keepKeys: {}", keepKeys);
        List<String> deleteKeys = new ArrayList<>();
        for (String key : previousKeys) {
            if (!keepKeys.contains(key)) {
                deleteKeys.add(key);
            }
        }
        boolean hasDeleteJobs = !deleteKeys.isEmpty();
        enqueueDeleteJob(deleteKeys, post.getId(), "POST", "UPDATE");

        if (!hasUpdateAsyncJobs && !hasDeleteJobs) {
            publishAfterCommit(post.getId(), "POST", "UPDATE");
        }

        return enrichCounts(postMapper.toResponse(updatedPost), updatedPost.getId());
    }

    private void enqueueAsyncMediaProcessing(
            MultipartFile file,
            String s3Key,
            String contentId,
            String contentTargetType,
            String operation,
            String mediaId,
            int orderIndex) {
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
                        contentId,
                        contentTargetType,
                        operation,
                        mediaId,
                        orderIndex
                );
                mediaCompressionJobPublisher.publish(job);
                return;
            }

            java.nio.file.Path tempPath = MediaTempFileStore.saveToTemp(file, "image-");
                MediaUploadJob job = new MediaUploadJob(
                    tempPath.toString(),
                    s3Key,
                    contentType,
                    contentId,
                    contentTargetType,
                    operation,
                    mediaId,
                    orderIndex
            );
            mediaUploadJobPublisher.publish(job);
        } catch (Exception ex) {
            log.warn("[MediaUpload] Failed to enqueue job for {}: {}",
                    file.getOriginalFilename(), ex.getMessage());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "userPosts"}, allEntries = true)
    public void deletePost(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        List<String> deleteKeys = collectPostMediaKeys(post);

        // Always soft delete so history/saved entries can still render the deleted state.
        post.setStatus(ContentStatusType.DELETED);
        if (post.getMedias() != null) {
            post.getMedias().clear();
        }
        postRepository.save(post);

        if (deleteKeys.isEmpty()) {
            publishAfterCommit(post.getId(), "POST", "DELETE");
        } else {
            enqueueDeleteJob(deleteKeys, post.getId(), "POST", "DELETE");
        }
    }

    private List<String> collectPostMediaKeys(Post post) {
        if (post.getMedias() == null || post.getMedias().isEmpty()) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        for (mediaservice.models.Media media : post.getMedias()) {
            String defaultFolder = media instanceof VideoMedia ? "social/videos" : "social/posts";
            addKey(keys, resolveKey(media.getUrl(), defaultFolder));

            if (media instanceof VideoMedia video && video.getThumbnailUrl() != null) {
                addKey(keys, resolveKey(video.getThumbnailUrl(), "social/videos"));
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
            log.warn("[MediaDelete] Failed to enqueue delete job: {}", ex.getMessage());
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

    private void publishPostCreatedAnalyticsAfterCommit(String postId, String userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            analyticsEventPublisher.publishPostCreated(postId, userId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                analyticsEventPublisher.publishPostCreated(postId, userId);
            }
        });
    }

    private void processTagsAndMentions(Post post) {
        if (post == null) return;
        String caption = post.getCaption();
        if (caption == null) caption = "";

        // 1) Process hashtags
        java.util.List<String> tags = TextTagParser.extractHashTags(caption);
        if (tags != null && !tags.isEmpty()) {
            java.util.Set<HashTag> tagEntities = post.getHashTags() != null ? new java.util.HashSet<>(post.getHashTags()) : new java.util.HashSet<>();
            for (String t : tags) {
                if (t == null || t.isBlank()) continue;
                HashTag hashTag = hashTagRepository.findByName(t).orElseGet(() -> {
                    HashTag h = new HashTag();
                    h.setName(t);
                    return hashTagRepository.save(h);
                });
                tagEntities.add(hashTag);
            }
            post.setHashTags(tagEntities);
            postRepository.save(post);
        }

        // 2) Process mentions
        java.util.List<String> mentions = TextTagParser.extractMentions(caption);
        if (mentions != null && !mentions.isEmpty()) {
            String senderId = post.getAccount() != null ? post.getAccount().getId() : null;
            for (String uname : mentions) {
                if (uname == null || uname.isBlank()) continue;
                userAccountRepository.findByUsername(uname).ifPresent(target -> {
                    notificationPublisher.publishNotification(target.getId(), senderId, "MENTION", "You were mentioned", post.getId());
                });
            }
        }
    }

    private void addKey(java.util.Collection<String> keys, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        keys.add(key);
    }

    private String resolveKey(String raw, String defaultFolder) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        // Always use extractRelativePath to get a clean path (no leading slash, no bucket, etc.)
        String normalized = mediaUrlBuilder.extractRelativePath(raw);

        // If it doesn't contain a folder segment, prepend the default folder
        if (!normalized.contains("/")) {
            return defaultFolder + "/" + normalized;
        }

        return normalized;
    }

    private String buildS3Key(String folder, String originalName) {
        String extension = extractExtension(originalName);
        String fileName = UUID.randomUUID() + extension;
        return folder + "/" + fileName;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > -1 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return "";
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userPosts", key = "#userId", unless = "#result == null || #result.isEmpty()")
    public List<PostResponse> getPostsByUserId(String userId) {
        return postRepository.findByAccount_Id(userId).stream()
                .map(p -> enrichCounts(postMapper.toResponse(p), p.getId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPostsByUserId(String userId, String viewerId) {
        return postRepository.findByAccount_Id(userId).stream()
                .map(p -> enrichCounts(postMapper.toResponse(p), p.getId(), viewerId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> searchPosts(String query, String viewerId, Pageable pageable) {
        return postRepository.searchPostsWithAuthorized(
                query,
                ContentStatusType.ACTIVE,
                VisibilityType.PUBLIC,
                VisibilityType.PRIVATE,
                VisibilityType.FRIENDS,
                RelationshipStatusType.ACCEPTED,
                VisibilityType.CUSTOM,
                RuleType.INCLUDE,
                RuleType.EXCLUDE,
                RelationshipStatusType.BLOCKED,
                viewerId,
                pageable
        ).map(p -> enrichCounts(postMapper.toResponse(p), p.getId(), viewerId));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "userPosts"}, allEntries = true)
    public PostResponse sharePost(String postId, String accountId, String caption, VisibilityType visibility) {
        Post originalPost = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Original post not found with id: " + postId));

        // If sharing a shared post, always point to the root/original post.
        Post rootPost = originalPost;
        while (rootPost.getSharedPost() != null) {
            rootPost = rootPost.getSharedPost();
        }

        UserAccount account = ensureUserSynced(accountId);
        if (account == null) {
            throw new RuntimeException("User not found or sync failed for id: " + accountId);
        }

        Post share = new Post();
        share.setAccount(account);
        share.setCaption(caption);
        share.setVisibility(visibility != null ? visibility : VisibilityType.PUBLIC);
        share.setStatus(ContentStatusType.ACTIVE);
        share.setSharedPost(rootPost);

        Post savedShare = postRepository.save(share);

        // Publish events for realtime socket updates:
        // 1. For the new share post
        publishAfterCommit(savedShare.getId(), "POST", "CREATE");

        // 2. For the original post (to broadcast that its share count has updated)
        postActivityPublisher.publish(rootPost.getId(), "SHARE", "CREATE", 
            java.util.Map.of("shares", postRepository.countBySharedPost_Id(rootPost.getId())));

        return enrichCounts(postMapper.toResponse(savedShare), savedShare.getId());
    }
}
