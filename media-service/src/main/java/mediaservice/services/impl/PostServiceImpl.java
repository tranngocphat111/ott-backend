package mediaservice.services.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.mappers.PostMapper;
import mediaservice.models.ImageMedia;
import mediaservice.models.Post;
import mediaservice.models.UserAccount;
import mediaservice.models.VideoMedia;
import mediaservice.models.ContentAccessControl;
import mediaservice.models.enums.*;
import mediaservice.repositories.CommentRepository;
import mediaservice.repositories.ContentAccessControlRepository;
import mediaservice.repositories.MediaRepository;
import mediaservice.repositories.PostRepository;
import mediaservice.repositories.ReactionRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.dtos.messages.MediaCompressionJob;
import mediaservice.dtos.messages.MediaDeleteJob;
import mediaservice.dtos.messages.MediaUploadJob;
import mediaservice.realtime.MediaRealtimePublisher;
import mediaservice.realtime.MediaRealtimeUpdate;
import mediaservice.services.PostService;
import mediaservice.services.MediaCompressionJobPublisher;
import mediaservice.services.MediaDeleteJobPublisher;
import mediaservice.services.MediaUploadJobPublisher;
import mediaservice.dtos.requests.AccessControlRequest;
import mediaservice.dtos.requests.MediaRequest;
import mediaservice.models.enums.MediaType;
import mediaservice.utils.MediaUrlBuilder;
import mediaservice.utils.MediaTempFileStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final ContentAccessControlRepository contentAccessControlRepository;
    private final MediaUrlBuilder mediaUrlBuilder;
    private final MediaCompressionJobPublisher mediaCompressionJobPublisher;
    private final MediaDeleteJobPublisher mediaDeleteJobPublisher;
    private final MediaUploadJobPublisher mediaUploadJobPublisher;
    private final MediaRealtimePublisher mediaRealtimePublisher;

    @PersistenceContext
    private EntityManager entityManager;

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
        UserAccount account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("User not found: " + accountId));

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
                UserAccount target = userAccountRepository.findById(req.getAccountId()).orElse(null);
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
                    media.setUrl(fileName);
                    media.setOrderIndex(orderIndex);
                    media.setCaption(mediaCaption);
                    media.setContent(savedPost);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, savedPost.getId(), "POST", "CREATE", media.getId(), orderIndex);
                    hasAsyncJobs = true;
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(fileName);
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
        List<String> keepKeys = new ArrayList<>();

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
                UserAccount target = userAccountRepository.findById(req.getAccountId()).orElse(null);
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

        // Replace medias
        if (post.getMedias() != null && !post.getMedias().isEmpty()) {
            mediaRepository.deleteAll(post.getMedias());
        }

        int orderIndex = 0;
        if (existingMedias != null && !existingMedias.isEmpty()) {
            for (MediaRequest req : existingMedias) {
                if (req == null || req.getUrl() == null) continue;
                String fileName = mediaUrlBuilder.extractFileName(req.getUrl());
                int idx = req.getOrderIndex();
                orderIndex = Math.max(orderIndex, idx + 1);

            String defaultFolder = req.getType() == MediaType.VIDEO_MEDIA
                ? "social/videos" : "social/posts";
            addKey(keepKeys, resolveKey(req.getUrl(), defaultFolder));
            addKey(keepKeys, resolveKey(req.getThumbnailUrl(), "social/videos"));

                if (req.getType() == MediaType.VIDEO_MEDIA) {
                    VideoMedia media = new VideoMedia();
                    media.setUrl(fileName);
                    media.setOrderIndex(idx);
                    media.setCaption(req.getCaption());
                    media.setContent(post);
                    mediaRepository.save(media);
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(fileName);
                    media.setOrderIndex(idx);
                    media.setCaption(req.getCaption());
                    media.setContent(post);
                    mediaRepository.save(media);
                }
            }
        }

        boolean hasUpdateAsyncJobs = false;
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file == null || file.isEmpty()) continue;
                String mediaCaption = (captions != null && i < captions.size())
                        ? (captions.get(i).isBlank() ? null : captions.get(i)) : null;
                int targetOrderIndex = orderIndex + i;

                String contentType = file.getContentType() != null ? file.getContentType() : "";
                boolean isVideo = contentType.startsWith("video/");
                String folder = isVideo ? "social/videos" : "social/posts";

                String s3Key = buildS3Key(folder, file.getOriginalFilename());
                String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
                log.info("[updatePost] Queued media #{} -> {} (stored as: {})",
                        targetOrderIndex, s3Key, fileName);
                addKey(keepKeys, s3Key);

                if (isVideo) {
                    VideoMedia media = new VideoMedia();
                    media.setUrl(fileName);
                    media.setOrderIndex(targetOrderIndex);
                    media.setCaption(mediaCaption);
                    media.setContent(post);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, post.getId(), "POST", "UPDATE", media.getId(), targetOrderIndex);
                    hasUpdateAsyncJobs = true;
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(fileName);
                    media.setOrderIndex(targetOrderIndex);
                    media.setCaption(mediaCaption);
                    media.setContent(post);
                    mediaRepository.save(media);
                    enqueueAsyncMediaProcessing(file, s3Key, post.getId(), "POST", "UPDATE", media.getId(), targetOrderIndex);
                    hasUpdateAsyncJobs = true;
                }
            }
        }

        Post updatedPost = postRepository.save(post);
        entityManager.flush();
        entityManager.refresh(updatedPost);

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

    private void addKey(List<String> keys, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        keys.add(key);
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
