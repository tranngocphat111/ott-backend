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

    /** Root comments (không có cha) của một post, có phân trang. */
    Page<CommentResponse> getRootCommentsByContentId(String contentId, Pageable pageable);

    /** Replies của một comment, có phân trang. */
    Page<CommentResponse> getRepliesByParentId(String parentId, Pageable pageable);

    /** Tổng số comment chưa xóa của một post (bao gồm cả replies). */
    long countByContentId(String contentId);
}

