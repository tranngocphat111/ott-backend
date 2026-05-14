package mediaservice.repositories;

import mediaservice.models.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryViewRepository extends JpaRepository<StoryView, String> {
    int countByStoryId(String storyId);
    boolean existsByStoryIdAndAccountId(String storyId, String accountId);
    Optional<StoryView> findByStoryIdAndAccountId(String storyId, String accountId);
    List<StoryView> findByStoryIdOrderByViewedAtDesc(String storyId);
    void deleteByStoryId(String storyId);
}
