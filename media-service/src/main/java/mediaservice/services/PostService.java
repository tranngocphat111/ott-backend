package mediaservice.services;

import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.models.enums.VisibilityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PostService {
    PostResponse createPost(PostRequest request);
    PostResponse createPost(String accountId, String caption, VisibilityType visibility,
                            List<MultipartFile> files, List<String> captions,
                            List<mediaservice.dtos.requests.AccessControlRequest> accessControls);
    PostResponse getPostById(String id);
    PostResponse getPostById(String id, String viewerId);


    List<PostResponse> getAllPosts();


    Page<PostResponse> getAllPosts(Pageable pageable);


    Page<PostResponse> findAllPostsWithAuthorized(Pageable pageable, String accountId);



    PostResponse updatePost(String id, PostRequest request);
    PostResponse updatePost(String id, String accountId, String caption, VisibilityType visibility,
                            List<MultipartFile> files, List<String> captions,
                            List<mediaservice.dtos.requests.AccessControlRequest> accessControls,
                            List<mediaservice.dtos.requests.MediaRequest> existingMedias);
    void deletePost(String id);
    List<PostResponse> getPostsByUserId(String userId);
    List<PostResponse> getPostsByUserId(String userId, String viewerId);
    PostResponse sharePost(String postId, String accountId, String caption, VisibilityType visibility);
}

