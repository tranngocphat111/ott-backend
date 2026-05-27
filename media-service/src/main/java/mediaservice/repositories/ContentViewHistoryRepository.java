package mediaservice.repositories;

import mediaservice.models.ContentViewHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ContentViewHistoryRepository extends JpaRepository<ContentViewHistory, String> {
    Page<ContentViewHistory> findByAccountIdOrderByViewedAtDesc(String accountId, Pageable pageable);
    Optional<ContentViewHistory> findByAccountIdAndContentId(String accountId, String contentId);

    @Transactional
    void deleteByAccountId(String accountId);

    @Transactional
    void deleteByContentId(String contentId);
}
