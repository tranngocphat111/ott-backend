package mediaservice.services;

import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface RelationshipService {
    // ── CRUD cơ bản ──────────────────────────────────────
    RelationshipResponse createRelationship(RelationshipRequest request);
    RelationshipResponse getRelationshipById(String id);
    List<RelationshipResponse> getAllRelationships();
    Page<RelationshipResponse> getAllRelationships(Pageable pageable);
    RelationshipResponse updateRelationship(String id, RelationshipRequest request);
    void deleteRelationship(String id);
    List<RelationshipResponse> getRelationshipsByUserId(String userId);

    // ── Chức năng kết bạn ────────────────────────────────
    /** Gửi lời mời kết bạn (tạo relationship PENDING). */
    RelationshipResponse sendFriendRequest(String requesterId, String receiverId);

    /** Chấp nhận lời mời kết bạn → status = ACCEPTED. */
    RelationshipResponse acceptFriendRequest(String relationshipId);

    /** Từ chối lời mời kết bạn → xóa bản ghi. */
    void rejectFriendRequest(String relationshipId);

    /** Hủy lời mời đã gửi → xóa bản ghi. */
    void cancelFriendRequest(String relationshipId);

    /** Hủy kết bạn (unfriend). */
    void unfriend(String relationshipId);

    /** Lấy danh sách bạn bè (ACCEPTED) của user. */
    List<RelationshipResponse> getFriends(String userId);

    /** Lấy danh sách lời mời kết bạn user nhận được (PENDING). */
    List<RelationshipResponse> getPendingRequests(String userId);

    /** Lấy danh sách lời mời kết bạn user đã gửi (PENDING). */
    List<RelationshipResponse> getSentRequests(String userId);

    /** Lấy trạng thái quan hệ giữa hai user (null nếu không có). */
    Optional<RelationshipResponse> getRelationshipBetween(String userId1, String userId2);

}

