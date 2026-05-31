package mediaservice.repositories;

import mediaservice.models.Post;
import mediaservice.models.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {
    List<Post> findByAccount_Id(String accountId);

    @Query(value = "SELECT p FROM Post p ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findallPosts(Pageable pageable);


//    hàm lấy tất cả post đã được thẩm quyền xem cho accountId
    /*
    * @param accountId: id tài khoản người dùng muốn xem post trên feed
    * */
    @Query(value =
    "SELECT p FROM Post p " +
    "JOIN FETCH p.account a  " +
    "WHERE p.status = :status AND " +
    "( " +
        "a.id = :accountId OR " +  // ⭐ THÊM DÒNG NÀY
        "p.visibility = :publicVis OR " +
        "( p.visibility = :privateVis AND a.id = :accountId ) OR " +
        "( p.visibility = :friendVis AND EXISTS " +
            "(" +
            "SELECT 1 FROM Relationship r " +
            "WHERE r.acceptedAt IS NOT NULL " +
            "AND r.status = :relationshipStatus " +
            "AND ((r.requester.id = :accountId AND r.receiver.id = a.id) " +
            "OR (r.receiver.id = :accountId AND r.requester.id = a.id))" +
            ")" +
        ") OR " +
        "( p.visibility = :customVis AND (" +
            "EXISTS " +
                "(SELECT 1 FROM ContentAccessControl ac " +
                "WHERE ac.ruleType = :whiteListRuleType " +
                "AND ac.content = p " +
                "AND ac.account.id = :accountId) " +
            " OR NOT EXISTS " +
                "(SELECT 1 FROM ContentAccessControl ac " +
                "WHERE ac.ruleType = :blackListRuleType " +
                "AND ac.content = p " +
                "AND ac.account.id = :accountId) " +
        ")) " +
    ") " +
    "ORDER BY p.createdAt DESC",
    countQuery =
            "SELECT count(p.id) FROM Post p " +
            "JOIN Account a ON a.id = p.account.id " +
            "WHERE p.status = :status AND " +
                "( " +
                    "a.id = :accountId OR " +  // ⭐ THÊM DÒNG NÀY
                    "p.visibility = :publicVis OR " +
                    "( p.visibility = :privateVis AND a.id = :accountId ) OR " +
                    "( p.visibility = :friendVis AND EXISTS " +
                    "(" +
                        "SELECT 1 FROM Relationship r " +
                        "WHERE r.acceptedAt IS NOT NULL " +
                        "AND r.status = :relationshipStatus " +
                        "AND ((r.requester.id = :accountId AND r.receiver.id = a.id) " +
                        "OR (r.receiver.id = :accountId AND r.requester.id = a.id))" +
                    ")" +
                    ") OR " +
                    "( p.visibility = :customVis AND (" +
                        "EXISTS " +
                            "(SELECT 1 FROM ContentAccessControl ac " +
                            "WHERE ac.ruleType = :whiteListRuleType " +
                            "AND ac.content = p " +
                            "AND ac.account.id = :accountId) " +
                        " OR NOT EXISTS " +
                            "(SELECT 1 FROM ContentAccessControl ac " +
                            "WHERE ac.ruleType = :blackListRuleType " +
                            "AND ac.content = p " +
                            "AND ac.account.id = :accountId) " +
                ")) " +
            ") "
)
    Page<Post> findAllPostsWithAuthorized(
        @Param("status") ContentStatusType status,                              // check trạng thái
        @Param("publicVis") VisibilityType publicVis,                           // check PUBLIC
        @Param("privateVis") VisibilityType privateVis,                         // check PRIVATE
        @Param("friendVis") VisibilityType friendVis,                           // check FRIEND
        @Param("relationshipStatus") RelationshipStatusType relationshipStatus,
        @Param("customVis") VisibilityType customVis,                           // check CUSTOM
        @Param("whiteListRuleType") RuleType whiteListRuleType,                 // check WHILELIST
        @Param("blackListRuleType") RuleType blackListRuleType,                 // check BLACKLIST
        @Param("accountId") String accountId,
        Pageable pageable
    );

    long countBySharedPost_Id(String originalPostId);

    @Query(value =
    "SELECT p FROM Post p " +
    "JOIN FETCH p.account a  " +
    "WHERE p.status = :status AND " +
    "( " +
    "   (:isHashtag = true AND EXISTS (SELECT 1 FROM p.hashTags ht WHERE LOWER(ht.name) LIKE LOWER(CONCAT('%', SUBSTRING(:query, 2), '%')))) " +
    "   OR " +
    "   (:isHashtag = false AND (" +
    "       LOWER(p.caption) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
    "       LOWER(a.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
    "       LOWER(a.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
    "   )) " +
    ") AND " +
    "( " +
        "a.id = :accountId OR " +
        "p.visibility = :publicVis OR " +
        "( p.visibility = :privateVis AND a.id = :accountId ) OR " +
        "( p.visibility = :friendVis AND EXISTS " +
            "(" +
            "SELECT 1 FROM Relationship r " +
            "WHERE r.acceptedAt IS NOT NULL " +
            "AND r.status = :relationshipStatus " +
            "AND ((r.requester.id = :accountId AND r.receiver.id = a.id) " +
            "OR (r.receiver.id = :accountId AND r.requester.id = a.id))" +
            ")" +
        ") OR " +
        "( p.visibility = :customVis AND (" +
            "EXISTS " +
                "(SELECT 1 FROM ContentAccessControl ac " +
                "WHERE ac.ruleType = :whiteListRuleType " +
                "AND ac.content = p " +
                "AND ac.account.id = :accountId) " +
             "OR NOT EXISTS " +
                "(SELECT 1 FROM ContentAccessControl ac " +
                "WHERE ac.ruleType = :blackListRuleType " +
                "AND ac.content = p " +
                "AND ac.account.id = :accountId) " +
        ")) " +
    ") AND NOT EXISTS (" +
    "    SELECT 1 FROM Relationship relBlock " +
    "    WHERE relBlock.status = :blockedStatus " +
    "      AND ((relBlock.requester.id = a.id AND relBlock.receiver.id = :accountId) " +
    "        OR (relBlock.receiver.id = a.id AND relBlock.requester.id = :accountId)) " +
    ") " +
    "ORDER BY p.createdAt DESC",
    countQuery =
            "SELECT count(p.id) FROM Post p " +
            "JOIN Account a ON a.id = p.account.id " +
            "WHERE p.status = :status AND " +
            "( " +
            "   (:isHashtag = true AND EXISTS (SELECT 1 FROM p.hashTags ht WHERE LOWER(ht.name) LIKE LOWER(CONCAT('%', SUBSTRING(:query, 2), '%')))) " +
            "   OR " +
            "   (:isHashtag = false AND (" +
            "       LOWER(p.caption) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "       LOWER(a.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "       LOWER(a.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "   )) " +
            ") AND " +
            "( " +
                "a.id = :accountId OR " +
                "p.visibility = :publicVis OR " +
                "( p.visibility = :privateVis AND a.id = :accountId ) OR " +
                "( p.visibility = :friendVis AND EXISTS " +
                "(" +
                    "SELECT 1 FROM Relationship r " +
                    "WHERE r.acceptedAt IS NOT NULL " +
                    "AND r.status = :relationshipStatus " +
                    "AND ((r.requester.id = :accountId AND r.receiver.id = a.id) " +
                    "OR (r.receiver.id = :accountId AND r.requester.id = a.id))" +
                ")" +
                ") OR " +
                "( p.visibility = :customVis AND (" +
                    "EXISTS " +
                        "(SELECT 1 FROM ContentAccessControl ac " +
                        "WHERE ac.ruleType = :whiteListRuleType " +
                        "AND ac.content = p " +
                        "AND ac.account.id = :accountId) " +
                     "OR NOT EXISTS " +
                        "(SELECT 1 FROM ContentAccessControl ac " +
                        "WHERE ac.ruleType = :blackListRuleType " +
                        "AND ac.content = p " +
                        "AND ac.account.id = :accountId) " +
            ")) " +
            ") AND NOT EXISTS (" +
            "    SELECT 1 FROM Relationship relBlock " +
            "    WHERE relBlock.status = :blockedStatus " +
            "      AND ((relBlock.requester.id = a.id AND relBlock.receiver.id = :accountId) " +
            "        OR (relBlock.receiver.id = a.id AND relBlock.requester.id = :accountId)) " +
            ")"
    )
    Page<Post> searchPostsWithAuthorized(
        @Param("query") String query,
        @Param("isHashtag") boolean isHashtag,
        @Param("status") ContentStatusType status,
        @Param("publicVis") VisibilityType publicVis,
        @Param("privateVis") VisibilityType privateVis,
        @Param("friendVis") VisibilityType friendVis,
        @Param("relationshipStatus") RelationshipStatusType relationshipStatus,
        @Param("customVis") VisibilityType customVis,
        @Param("whiteListRuleType") RuleType whiteListRuleType,
        @Param("blackListRuleType") RuleType blackListRuleType,
        @Param("blockedStatus") RelationshipStatusType blockedStatus,
        @Param("accountId") String accountId,
        Pageable pageable
    );
}

