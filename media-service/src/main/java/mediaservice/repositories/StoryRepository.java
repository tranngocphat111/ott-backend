package mediaservice.repositories;

import mediaservice.models.Story;
import mediaservice.models.User;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface StoryRepository extends JpaRepository<Story, String> {
}
