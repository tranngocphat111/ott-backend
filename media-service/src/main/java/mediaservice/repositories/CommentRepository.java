package mediaservice.repositories;

import mediaservice.models.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findByParentCommentId(String parentId);
    List<Comment> findByParentCommentIsNull();
}

