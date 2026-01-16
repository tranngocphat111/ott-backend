package mediaservice.repositories;

import mediaservice.models.Post;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PostRepository extends JpaRepository<Post, String> {
}
