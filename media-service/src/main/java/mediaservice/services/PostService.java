package mediaservice.services;

import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PostService {
    List<PostResponse> getAllPosts();

    @Transactional(readOnly = true)
    List<PostResponse> getAllPostsByUserId(String userId);

    PostResponse getPostById(String id);
    PostResponse createPost(PostRequest request);
    PostResponse updatePost(String id, PostRequest request);
    void deletePost(String id);
}

