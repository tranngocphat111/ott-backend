package mediaservice.repositories;

import mediaservice.models.Tag;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface TagRepository extends JpaRepository<Tag, String> {
}
