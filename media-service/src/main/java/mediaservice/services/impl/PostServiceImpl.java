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
import mediaservice.services.PostService;
import mediaservice.services.S3Service;
import mediaservice.dtos.requests.AccessControlRequest;
import mediaservice.dtos.requests.MediaRequest;
import mediaservice.models.enums.MediaType;
import mediaservice.utils.MediaUrlBuilder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

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
    private final ContentAccessControlRepository contentAccessControlRepository;
    private final MediaUrlBuilder mediaUrlBuilder;

    @PersistenceContext
    private EntityManager entityManager;

    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(
        Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()))
    );

    private static class UploadResult {
        private final int orderIndex;
        private final String fileName;
        private final boolean isVideo;
        private final String caption;

        private UploadResult(int orderIndex, String fileName, boolean isVideo, String caption) {
            this.orderIndex = orderIndex;
            this.fileName = fileName;
            this.isVideo = isVideo;
            this.caption = caption;
        }
    }

    @PreDestroy
    void shutdownUploadExecutor() {
        uploadExecutor.shutdown();
        try {
            if (!uploadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                uploadExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            uploadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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

        // 3. Upload each media file → S3 → save Media row (với caption per-file)
        if (files != null) {
            List<CompletableFuture<UploadResult>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                final int index = i;
                MultipartFile file = files.get(i);
                if (file == null || file.isEmpty()) continue;
                String mediaCaption = (captions != null && index < captions.size())
                        ? (captions.get(index).isBlank() ? null : captions.get(index)) : null;

                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String contentType = file.getContentType() != null ? file.getContentType() : "";
                        boolean isVideo = contentType.startsWith("video/");
                        String folder = isVideo ? "social/videos" : "social/posts";
                        String s3Key = s3Service.uploadFile(file, folder);
                        String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
                        log.info("[createPost] Uploaded media #{} -> {} (stored as: {})",
                                index, s3Key, fileName);
                        return new UploadResult(index, fileName, isVideo, mediaCaption);
                    } catch (Exception e) {
                        log.error("[createPost] S3 upload FAILED for file '{}': {}",
                                file.getOriginalFilename(), e.getMessage(), e);
                        throw new RuntimeException("Failed to upload media: " + file.getOriginalFilename(), e);
                    }
                }, uploadExecutor));
            }

            List<UploadResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(java.util.Comparator.comparingInt(r -> r.orderIndex))
                    .toList();

            for (UploadResult result : results) {
                if (result.isVideo) {
                    VideoMedia media = new VideoMedia();
                    media.setUrl(result.fileName);
                    media.setOrderIndex(result.orderIndex);
                    media.setCaption(result.caption);
                    media.setContent(savedPost);
                    mediaRepository.save(media);
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(result.fileName);
                    media.setOrderIndex(result.orderIndex);
                    media.setCaption(result.caption);
                    media.setContent(savedPost);
                    mediaRepository.save(media);
                }
            }
        }

        // 4. Flush và refresh để lấy medias list mới nhất từ DB vào entity
        //    (tránh trả về Hibernate cache cũ không có media)
        entityManager.flush();
        entityManager.refresh(savedPost);

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

        if (files != null) {
            List<CompletableFuture<UploadResult>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                final int index = i;
                MultipartFile file = files.get(index);
                if (file == null || file.isEmpty()) continue;
                String mediaCaption = (captions != null && index < captions.size())
                        ? (captions.get(index).isBlank() ? null : captions.get(index)) : null;
                int targetOrderIndex = orderIndex + index;

                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String contentType = file.getContentType() != null ? file.getContentType() : "";
                        boolean isVideo = contentType.startsWith("video/");
                        String folder = isVideo ? "social/videos" : "social/posts";

                        String s3Key = s3Service.uploadFile(file, folder);
                        String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
                        log.info("[updatePost] Uploaded media #{} -> {} (stored as: {})",
                                targetOrderIndex, s3Key, fileName);
                        return new UploadResult(targetOrderIndex, fileName, isVideo, mediaCaption);
                    } catch (Exception e) {
                        log.error("[updatePost] S3 upload FAILED for file '{}': {}",
                                file.getOriginalFilename(), e.getMessage(), e);
                        throw new RuntimeException("Failed to upload media: " + file.getOriginalFilename(), e);
                    }
                }, uploadExecutor));
            }

            List<UploadResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(java.util.Comparator.comparingInt(r -> r.orderIndex))
                    .toList();

            for (UploadResult result : results) {
                if (result.isVideo) {
                    VideoMedia media = new VideoMedia();
                    media.setUrl(result.fileName);
                    media.setOrderIndex(result.orderIndex);
                    media.setCaption(result.caption);
                    media.setContent(post);
                    mediaRepository.save(media);
                } else {
                    ImageMedia media = new ImageMedia();
                    media.setUrl(result.fileName);
                    media.setOrderIndex(result.orderIndex);
                    media.setCaption(result.caption);
                    media.setContent(post);
                    mediaRepository.save(media);
                }
            }
        }

        Post updatedPost = postRepository.save(post);
        entityManager.flush();
        entityManager.refresh(updatedPost);

        return enrichCounts(postMapper.toResponse(updatedPost), updatedPost.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "userPosts"}, allEntries = true)
    public void deletePost(String id) {
        if (!postRepository.existsById(id)) {
            throw new RuntimeException("Post not found with id: " + id);
        }
        postRepository.deleteById(id);
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
