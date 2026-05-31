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
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Comment c WHERE c.content.id = :contentId " +
        "AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT 1 FROM Comment child WHERE child.parentComment = c AND child.isDeleted = false)))"
    )
    List<Comment> findByContent_IdAndIsDeletedFalse(@org.springframework.data.repository.query.Param("contentId") String contentId);
    List<Comment> findByContent_IdOrderByCreatedAtAsc(String contentId);

    /** Root comments (depth=0) của một post, có phân trang */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Comment c WHERE c.content.id = :contentId " +
        "AND c.parentComment IS NULL " +
        "AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT 1 FROM Comment child WHERE child.parentComment = c AND child.isDeleted = false)))"
    )
    Page<Comment> findByContent_IdAndParentCommentIsNullAndIsDeletedFalse(
            @org.springframework.data.repository.query.Param("contentId") String contentId, Pageable pageable);

    /** Replies của một comment cha, có phân trang */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Comment c WHERE c.parentComment.id = :parentId " +
        "AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT 1 FROM Comment child WHERE child.parentComment = c AND child.isDeleted = false)))"
    )
    Page<Comment> findByParentComment_IdAndIsDeletedFalse(
            @org.springframework.data.repository.query.Param("parentId") String parentId, Pageable pageable);

    /** Đếm số comment (không xóa) của một bài post */
    long countByContent_IdAndIsDeletedFalse(String contentId);

    void deleteByContent_Id(String contentId);
}
