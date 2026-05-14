package mediaservice.repositories;

import mediaservice.models.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findByParentCommentId(String parentId);
    List<Comment> findByParentCommentIsNull();
    long countByContent_Id(String contentId);
    List<Comment> findByContent_IdAndIsDeletedFalse(String contentId);
    List<Comment> findByContent_IdOrderByCreatedAtAsc(String contentId);

    /** Root comments (depth=0) của một post, có phân trang */
    Page<Comment> findByContent_IdAndParentCommentIsNullAndIsDeletedFalse(
            String contentId, Pageable pageable);

    /** Replies của một comment cha, có phân trang */
    Page<Comment> findByParentComment_IdAndIsDeletedFalse(
            String parentId, Pageable pageable);

    /** Đếm số comment (không xóa) của một bài post */
    long countByContent_IdAndIsDeletedFalse(String contentId);
}

