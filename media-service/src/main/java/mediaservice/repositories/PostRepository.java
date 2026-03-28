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
                "WHERE (r.status = :relationshipStatus AND r.receiver.id = :accountId) OR (r.requester.id = :accountId)" +
                ")" +
        ") OR " +
        "( p.visibility = :customVis AND " +
                "EXISTS " +
                    "(SELECT 1 FROM ContentAccessControl ac " +
                    "WHERE ac.ruleType = :whiteListRuleType " +
                    "AND ac.content = p " +
                    "AND ac.account.id = :accountId) " +
                " OR NOT EXISTS " +
                    "(SELECT ac FROM ContentAccessControl ac " +
                    "WHERE ac.ruleType = :blackListRuleType " +
                    "AND ac.content = p " +
                    "AND ac.account.id <> :accountId) " +
        ") " +
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
                        "WHERE (r.status = :relationshipStatus AND r.receiver.id = :accountId) OR (r.requester.id = :accountId)" +
                    ")" +
                    ") OR " +
                    "( p.visibility = :customVis AND " +
                        "EXISTS " +
                            "(SELECT 1 FROM ContentAccessControl ac " +
                            "WHERE ac.ruleType = :whiteListRuleType " +
                            "AND ac.content = p " +
                            "AND ac.account.id = :accountId) " +
                        " OR NOT EXISTS " +
                            "(SELECT ac FROM ContentAccessControl ac " +
                            "WHERE ac.ruleType = :blackListRuleType " +
                            "AND ac.content = p " +
                            "AND ac.account.id <> :accountId) " +
                ") " +
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
}

