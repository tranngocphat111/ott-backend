package mediaservice.repositories;

import mediaservice.models.Relationship;
import mediaservice.models.enums.RelationshipStatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipRepository extends JpaRepository<Relationship, String> {

    /** Tìm theo requester + receiver (một chiều) */
    Optional<Relationship> findByRequesterIdAndReceiverId(String requesterId, String receiverId);

    /** Tìm theo cả hai chiều (bất kể ai gửi) */
    @Query("SELECT r FROM Relationship r WHERE " +
           "(r.requester.id = :userId1 AND r.receiver.id = :userId2) OR " +
           "(r.requester.id = :userId2 AND r.receiver.id = :userId1)")
    Optional<Relationship> findBetweenUsers(@Param("userId1") String userId1, @Param("userId2") String userId2);

    /** Lấy danh sách lời mời kết bạn đang chờ mà user nhận được (receiver) */
    List<Relationship> findByReceiverIdAndStatus(String receiverId, RelationshipStatusType status);
    List<Relationship> findByReceiverIdAndStatus(String receiverId, RelationshipStatusType status, org.springframework.data.domain.Pageable pageable);

    /** Lấy danh sách lời mời kết bạn user đã gửi (requester) */
    List<Relationship> findByRequesterIdAndStatus(String requesterId, RelationshipStatusType status);

    /** Lấy tất cả bạn bè (ACCEPTED) của một user */
    @Query("SELECT r FROM Relationship r WHERE " +
           "(r.requester.id = :userId OR r.receiver.id = :userId) AND r.status = :status")
    List<Relationship> findFriendsByUserId(@Param("userId") String userId,
                                           @Param("status") RelationshipStatusType status);

    @Query("SELECT r FROM Relationship r WHERE " +
           "(r.requester.id = :userId OR r.receiver.id = :userId) AND r.status = :status")
    List<Relationship> findFriendsByUserId(@Param("userId") String userId,
                                           @Param("status") RelationshipStatusType status,
                                           org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(r) > 0 FROM Relationship r WHERE r.status = 'BLOCKED' AND " +
           "((r.requester.id = :userId1 AND r.receiver.id = :userId2) OR " +
           "(r.requester.id = :userId2 AND r.receiver.id = :userId1))")
    boolean existsBlockBetween(@Param("userId1") String userId1, @Param("userId2") String userId2);

    @Query("SELECT COUNT(r) > 0 FROM Relationship r WHERE r.status = 'ACCEPTED' AND " +
           "((r.requester.id = :userId1 AND r.receiver.id = :userId2) OR " +
           "(r.requester.id = :userId2 AND r.receiver.id = :userId1))")
    boolean isFriend(@Param("userId1") String userId1, @Param("userId2") String userId2);

    List<Relationship> findByBlockedByIdAndStatus(String blockedById, RelationshipStatusType status);
}
