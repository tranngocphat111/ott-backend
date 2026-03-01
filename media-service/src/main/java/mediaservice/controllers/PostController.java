package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.CommentResponse;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.dtos.responses.ReactionResponse;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;
import mediaservice.models.enums.VisibilityType;
import mediaservice.services.CommentService;
import mediaservice.services.PostService;
import mediaservice.services.ReactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final ReactionService reactionService;
    private final CommentService commentService;

    /** POST /posts – tạo bài post mới kèm upload ảnh lên S3. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("accountId") String accountId,
            @RequestParam("caption") String caption,
            @RequestParam(value = "visibility", defaultValue = "PUBLIC") String visibility,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        VisibilityType vis = VisibilityType.valueOf(visibility.toUpperCase());
        return ResponseEntity.ok(postService.createPost(accountId, caption, vis, files));
    }

    /** GET /posts  – tất cả bài post */
    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    /** GET /posts/page  – có phân trang */
    @GetMapping("/page")
    public ResponseEntity<Page<PostResponse>> getPostsPaged(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(postService.getAllPosts(pageable));
    }

    /** GET /posts/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable String id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    /** GET /posts/user/{userId} */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getPostsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(postService.getPostsByUserId(userId));
    }

    /** PUT /posts/{id} – cập nhật bài post */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable String id,
            @RequestBody PostRequest request) {
        return ResponseEntity.ok(postService.updatePost(id, request));
    }

    /** DELETE /posts/{id} – xoá bài post */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable String id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    /* ─── Reaction endpoints ─── */

    /**
     * POST /posts/{postId}/like?accountId=xxx&reactionType=LIKE
     * Toggle like – trả về reaction nếu đã like, hoặc {"liked":false} nếu unlike
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable String postId,
            @RequestParam String accountId,
            @RequestParam(defaultValue = "LIKE") String reactionType) {
        ReactionType rt = ReactionType.valueOf(reactionType.toUpperCase());
        ReactionResponse reaction = reactionService.toggleReaction(
                accountId, postId, ReactionTargetType.POST, rt);
        boolean liked = reaction != null;
        long total = reactionService.getReactionsByTargetId(postId).size();
        return ResponseEntity.ok(Map.of(
                "liked", liked,
                "totalReactions", total,
                "reaction", liked ? reaction : Map.of()
        ));
    }

    /** GET /posts/{postId}/reactions – lấy tất cả reactions của bài post */
    @GetMapping("/{postId}/reactions")
    public ResponseEntity<List<ReactionResponse>> getPostReactions(@PathVariable String postId) {
        return ResponseEntity.ok(reactionService.getReactionsByTargetId(postId));
    }

    /* ─── Comment endpoints ─── */

    /** GET /posts/{postId}/comments – lấy tất cả comments của bài post */
    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getPostComments(@PathVariable String postId) {
        return ResponseEntity.ok(commentService.getCommentsByContentId(postId));
    }

    /** POST /posts/{postId}/comments – thêm comment vào bài post */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String postId,
            @RequestParam String accountId,
            @RequestParam String text,
            @RequestParam(required = false) String parentCommentId) {
        mediaservice.dtos.requests.CommentRequest req = new mediaservice.dtos.requests.CommentRequest();
        req.setText(text);
        req.setAccountId(accountId);
        req.setContentId(postId);
        req.setParentCommentId(parentCommentId);
        return ResponseEntity.ok(commentService.createComment(req));
    }

    /** DELETE /posts/{postId}/comments/{commentId} – xoá (soft-delete) comment */
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String postId,
            @PathVariable String commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}
