package mediaservice.repositories;

import mediaservice.models.SavedContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedContentRepository extends JpaRepository<SavedContent, String> {
    Page<SavedContent> findByAccountIdOrderBySavedAtDesc(String accountId, Pageable pageable);
    Optional<SavedContent> findByAccountIdAndContentId(String accountId, String contentId);
    boolean existsByAccountIdAndContentId(String accountId, String contentId);

    void deleteByContentId(String contentId);
}
