package mediaservice.repositories;

import mediaservice.models.Comment;
import mediaservice.models.VideoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoItemRepository extends JpaRepository<VideoItem, String> {

}
