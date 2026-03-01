package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.models.enums.VisibilityType;
import mediaservice.services.PostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * POST /posts  – tạo bài post mới, kèm upload ảnh lên S3.
     * Content-Type: multipart/form-data
     * Fields: accountId (required), caption (required),
     *         visibility (optional, default PUBLIC), files[] (optional)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("accountId") String accountId,
            @RequestParam("caption") String caption,
            @RequestParam(value = "visibility", defaultValue = "PUBLIC") String visibility,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        VisibilityType vis = VisibilityType.valueOf(visibility.toUpperCase());
        return ResponseEntity.ok(postService.createPost(accountId, caption, vis, files));
    }

    /** GET /posts  – trả về tất cả bài post (không phân trang) */
    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    /** GET /posts/page  – trả về bài post có phân trang */
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
}
