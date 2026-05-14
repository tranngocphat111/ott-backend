package mediaservice.repositories;

import mediaservice.models.Story;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RuleType;
import mediaservice.models.enums.VisibilityType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, String> {
    List<Story> findByExpireAtAfter(LocalDateTime dateTime);
    List<Story> findByIsHighlight(boolean isHighlight);

    @Query("SELECT s FROM Story s LEFT JOIN FETCH s.account ORDER BY s.createdAt DESC")
    List<Story> findAllWithAccount();

    @Query(
        "SELECT s FROM Story s " +
        "JOIN FETCH s.account a " +
        "WHERE s.status = :status " +
        "AND (s.expireAt IS NULL OR s.expireAt > :now) " +
        "AND ( " +
            "a.id = :accountId OR " +
            "s.visibility = :publicVis OR " +
            "(s.visibility = :privateVis AND a.id = :accountId) OR " +
            "(s.visibility = :friendVis AND EXISTS (" +
                "SELECT 1 FROM Relationship r " +
                "WHERE r.status = :relationshipStatus " +
                "AND ((r.requester.id = a.id AND r.receiver.id = :accountId) " +
                    "OR (r.receiver.id = a.id AND r.requester.id = :accountId))" +
            ")) OR " +
            "(s.visibility = :customVis AND (" +
                "EXISTS (SELECT 1 FROM ContentAccessControl ac " +
                "WHERE ac.ruleType = :whiteListRuleType " +
                "AND ac.content = s " +
                "AND ac.account.id = :accountId) " +
                "OR NOT EXISTS (SELECT ac FROM ContentAccessControl ac " +
                "WHERE ac.ruleType = :blackListRuleType " +
                "AND ac.content = s " +
                "AND ac.account.id = :accountId)" +
            ")) " +
        ") " +
        "ORDER BY s.createdAt DESC"
    )
    List<Story> findAuthorizedActiveStories(
        @Param("status") ContentStatusType status,
        @Param("publicVis") VisibilityType publicVis,
        @Param("privateVis") VisibilityType privateVis,
        @Param("friendVis") VisibilityType friendVis,
        @Param("relationshipStatus") RelationshipStatusType relationshipStatus,
        @Param("customVis") VisibilityType customVis,
        @Param("whiteListRuleType") RuleType whiteListRuleType,
        @Param("blackListRuleType") RuleType blackListRuleType,
        @Param("accountId") String accountId,
        @Param("now") LocalDateTime now
    );
}

