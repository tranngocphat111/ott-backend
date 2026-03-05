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
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.VisibilityType;
import mediaservice.repositories.CommentRepository;
import mediaservice.repositories.MediaRepository;
import mediaservice.repositories.PostRepository;
import mediaservice.repositories.ReactionRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.PostService;
import mediaservice.services.S3Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
        Post savedPost = postRepository.save(post);
        return enrichCounts(postMapper.toResponse(savedPost), savedPost.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allPosts", "userPosts"}, allEntries = true)
    public PostResponse createPost(String accountId, String caption,
                                   VisibilityType visibility,
                                   List<MultipartFile> files,
                                   List<String> captions) {
        // 1. Resolve author
        UserAccount account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("User not found: " + accountId));

        // 2. Persist the Post
        Post post = new Post();
        post.setAccount(account);
        post.setCaption(caption);
        post.setVisibility(visibility != null ? visibility : VisibilityType.PUBLIC);
        Post savedPost = postRepository.save(post);

        // 3. Upload each media file → S3 → save Media row (với caption per-file)
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file == null || file.isEmpty()) continue;
                // Lấy caption tương ứng với file này (nếu có)
                String mediaCaption = (captions != null && i < captions.size())
                        ? (captions.get(i).isBlank() ? null : captions.get(i)) : null;
                try {
                    String contentType = file.getContentType() != null ? file.getContentType() : "";
                    boolean isVideo = contentType.startsWith("video/");
                    String folder = isVideo ? "social/videos" : "social/posts";
                    String s3Key = s3Service.uploadFile(file, folder);

                    if (isVideo) {
                        VideoMedia media = new VideoMedia();
                        media.setUrl(s3Key);
                        media.setOrderIndex(i);
                        media.setCaption(mediaCaption);
                        media.setContent(savedPost);
                        mediaRepository.save(media);
                    } else {
                        ImageMedia media = new ImageMedia();
                        media.setUrl(s3Key);
                        media.setOrderIndex(i);
                        media.setCaption(mediaCaption);
                        media.setContent(savedPost);
                        mediaRepository.save(media);
                    }
                    log.info("[createPost] Saved media #{} caption='{}' -> {}", i, mediaCaption, s3Key);
                } catch (Exception e) {
                    log.error("[createPost] S3 upload FAILED for file '{}': {}",
                            file.getOriginalFilename(), e.getMessage(), e);
                    throw new RuntimeException("Failed to upload media: " + file.getOriginalFilename(), e);
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
        return postRepository.findAll(pageable)
                .map(p -> enrichCounts(postMapper.toResponse(p), p.getId()));
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
