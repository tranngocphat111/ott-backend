package mediaservice.repositories;

import mediaservice.models.UserAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByUsername(String username);

    @Query(
        "SELECT u FROM UserAccount u " +
        "WHERE u.id <> :accountId " +
        "AND NOT EXISTS (" +
            "SELECT 1 FROM Relationship r " +
            "WHERE (r.requester.id = :accountId AND r.receiver.id = u.id) " +
               "OR (r.receiver.id = :accountId AND r.requester.id = u.id)" +
        ") " +
        "ORDER BY u.createdAt DESC"
    )
    List<UserAccount> findSuggestedUsersForStoryReel(@Param("accountId") String accountId, Pageable pageable);
}

