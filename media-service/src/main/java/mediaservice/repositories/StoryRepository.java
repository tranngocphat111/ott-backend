package mediaservice.repositories;

import mediaservice.models.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, String> {
    List<Story> findByExpireAtAfter(LocalDateTime dateTime);
    List<Story> findByIsHighlight(boolean isHighlight);
}

