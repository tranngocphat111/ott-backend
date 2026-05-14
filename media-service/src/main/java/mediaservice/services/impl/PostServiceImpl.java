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
import mediaservice.repositories.CommentRepository;
import mediaservice.repositories.ContentAccessControlRepository;
import mediaservice.repositories.MediaRepository;
import mediaservice.repositories.PostRepository;
import mediaservice.repositories.ReactionRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.AnalyticsEventPublisher;
import mediaservice.services.MediaCompressionJobPublisher;
import mediaservice.services.MediaDeleteJobPublisher;
import mediaservice.services.MediaUploadJobPublisher;
import mediaservice.services.PostService;
import mediaservice.services.S3Service;
import mediaservice.utils.MediaTempFileStore;
import mediaservice.utils.MediaUrlBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
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

    private final mediaservice.services.UserSyncService userSyncService;
    private final mediaservice.mappers.ContentAccessControlMapper contentAccessControlMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /* ─── helper: ensure author info is fresh ─ */
    private UserAccount ensureUserSynced(String userId) {
        return userSyncService.syncUser(userId)
                .orElseGet(() -> userAccountRepository.findById(userId).orElse(null));
    }

    /* ─── helper: fill totalReactions / totalComments from DB ─ */
    private PostResponse enrichCounts(PostResponse response, String postId) {
        response.setTotalReactions(
                (int) reactionRepository.countByTargetIdAndTargetType(postId, ReactionTargetType.POST));
        response.setTotalComments(
                (int) commentRepository.countByContent_Id(postId));
        // totalShares has no backing model yet → stays 0
        return response;
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
        String userId = savedPost.getAccount() != null ? savedPost.getAccount().getId() : null;
        analyticsEventPublisher.publishPostCreated(savedPost.getId(), userId);
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

        analyticsEventPublisher.publishPostCreated(savedPost.getId(), accountId);
        if (!hasAsyncJobs) {
            publishAfterCommit(savedPost.getId(), "POST", "CREATE");
        }

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
    @Cacheable(value = "allPosts", unless = "#result == null || #result.isEmpty()")
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
                ).map(p -> enrichCounts(postMapper.toResponse(p), p.getId()));
    }


    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "userPosts"}, allEntries = true)
    public PostResponse updatePost(String id, PostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        postMapper.updateEntity(request, post);
        Post updatedPost = postRepository.save(post);
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

        // Update access controls
        List<ContentAccessControl> existingControls =
                contentAccessControlRepository.findByContent(post);
        if (existingControls != null && !existingControls.isEmpty()) {
            contentAccessControlRepository.deleteAll(existingControls);
        }

        if (visibility == VisibilityType.CUSTOM && accessControls != null && !accessControls.isEmpty()) {
            List<ContentAccessControl> controls = new java.util.ArrayList<>();
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
            if (!controls.isEmpty()) {
                contentAccessControlRepository.saveAll(controls);
                post.setAccessControls(new java.util.HashSet<>(controls));
            }
        } else {
            post.setAccessControls(new java.util.HashSet<>());
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
        postRepository.delete(post);

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
}
