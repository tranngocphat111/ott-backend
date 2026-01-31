package mediaservice.services;

import mediaservice.dtos.requests.CommentRequest;
import mediaservice.dtos.responses.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommentService {
    CommentResponse createComment(CommentRequest request);
    CommentResponse getCommentById(String id);
    List<CommentResponse> getAllComments();
    Page<CommentResponse> getAllComments(Pageable pageable);
    CommentResponse updateComment(String id, CommentRequest request);
    void deleteComment(String id);
    List<CommentResponse> getCommentsByContentId(String contentId);
    List<CommentResponse> getCommentsByParentId(String parentId);
}

