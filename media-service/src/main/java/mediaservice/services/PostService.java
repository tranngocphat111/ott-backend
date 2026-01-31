package mediaservice.services;

import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostService {
    PostResponse createPost(PostRequest request);
    PostResponse getPostById(String id);
    List<PostResponse> getAllPosts();
    Page<PostResponse> getAllPosts(Pageable pageable);
    PostResponse updatePost(String id, PostRequest request);
    void deletePost(String id);
    List<PostResponse> getPostsByUserId(String userId);
}

