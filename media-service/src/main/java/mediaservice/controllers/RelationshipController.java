package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.responses.RelationshipResponse;
import mediaservice.services.RelationshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RelationshipController
 * Base URL: /media/api/relationships
 *
 * Endpoints kết bạn:
 *  POST   /relationships/send?requesterId=&receiverId=   → gửi lời mời
 *  PATCH  /relationships/{id}/accept                     → chấp nhận
 *  DELETE /relationships/{id}/reject                     → từ chối
 *  DELETE /relationships/{id}/cancel                     → hủy lời mời đã gửi
 *  DELETE /relationships/{id}/unfriend                   → hủy kết bạn
 *  GET    /relationships/friends/{userId}                → danh sách bạn bè
 *  GET    /relationships/pending/{userId}               → lời mời nhận được
 *  GET    /relationships/sent/{userId}                  → lời mời đã gửi
 *  GET    /relationships/status?userId1=&userId2=       → trạng thái quan hệ
 */
@RestController
@RequestMapping("/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    /* ─── Gửi lời mời kết bạn ─────────────────────────────── */
    @PostMapping("/send")
    public ResponseEntity<RelationshipResponse> sendFriendRequest(
            @RequestParam String requesterId,
            @RequestParam String receiverId) {
        return ResponseEntity.ok(relationshipService.sendFriendRequest(requesterId, receiverId));
    }

    /* ─── Chấp nhận lời mời ───────────────────────────────── */
    @PatchMapping("/{id}/accept")
    public ResponseEntity<RelationshipResponse> acceptFriendRequest(@PathVariable String id) {
        return ResponseEntity.ok(relationshipService.acceptFriendRequest(id));
    }

    /* ─── Từ chối lời mời ─────────────────────────────────── */
    @DeleteMapping("/{id}/reject")
    public ResponseEntity<Void> rejectFriendRequest(@PathVariable String id) {
        relationshipService.rejectFriendRequest(id);
        return ResponseEntity.noContent().build();
    }

    /* ─── Hủy lời mời đã gửi ──────────────────────────────── */
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelFriendRequest(@PathVariable String id) {
        relationshipService.cancelFriendRequest(id);
        return ResponseEntity.noContent().build();
    }

    /* ─── Hủy kết bạn ─────────────────────────────────────── */
    @DeleteMapping("/{id}/unfriend")
    public ResponseEntity<Void> unfriend(@PathVariable String id) {
        relationshipService.unfriend(id);
        return ResponseEntity.noContent().build();
    }

    /* ─── Danh sách bạn bè ────────────────────────────────── */
    @GetMapping("/friends/{userId}")
    public ResponseEntity<List<RelationshipResponse>> getFriends(@PathVariable String userId) {
        return ResponseEntity.ok(relationshipService.getFriends(userId));
    }

    /* ─── Lời mời nhận được (PENDING) ────────────────────── */
    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<RelationshipResponse>> getPendingRequests(@PathVariable String userId) {
        return ResponseEntity.ok(relationshipService.getPendingRequests(userId));
    }

    /* ─── Lời mời đã gửi (PENDING) ─────────────────────────── */
    @GetMapping("/sent/{userId}")
    public ResponseEntity<List<RelationshipResponse>> getSentRequests(@PathVariable String userId) {
        return ResponseEntity.ok(relationshipService.getSentRequests(userId));
    }

    /* ─── Trạng thái quan hệ giữa hai user ──────────────────── */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRelationshipStatus(
            @RequestParam String userId1,
            @RequestParam String userId2) {
        Optional<RelationshipResponse> rel = relationshipService.getRelationshipBetween(userId1, userId2);
        if (rel.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "NONE"));
        }
        RelationshipResponse r = rel.get();
        return ResponseEntity.ok(Map.of(
                "status",         r.getStatus().name(),
                "relationshipId", r.getId(),
                "requesterId",    r.getRequesterId(),
                "receiverId",     r.getReceiverId()
        ));
    }
}
