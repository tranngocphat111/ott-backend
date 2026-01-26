package mediaservice.repositories;

import mediaservice.models.Comment;
import mediaservice.models.StoryMusic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryMusicRepository extends JpaRepository<StoryMusic, String> {

}
