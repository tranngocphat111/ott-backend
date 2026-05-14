package mediaservice.repositories;

import mediaservice.models.Comment;
import mediaservice.models.StoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryItemRepository extends JpaRepository<StoryItem, String> {

}
