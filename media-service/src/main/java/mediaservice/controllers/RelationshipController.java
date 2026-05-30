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
 *  PATCH  /relationships/{id}/block?blockerId=           → chặn
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


    @GetMapping
    public ResponseEntity<RelationshipResponse> getRelationshipOf(
            @RequestParam String user1,
            @RequestParam String user2
    ) {
        return ResponseEntity.ok(relationshipService.getRelationshipBetween(user1, user2).orElse(null));
    }


    @GetMapping ("/send")
    public ResponseEntity<RelationshipResponse> getFriendRequest(
            @RequestParam String requesterId,
            @RequestParam String receiverId) {
        return ResponseEntity.ok(relationshipService.getRelationshipBetween(requesterId, receiverId).orElse(null));
    }
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

    /* ─── Chặn người dùng ─────────────────────────────────── */
    @PatchMapping("/{id}/block")
    public ResponseEntity<RelationshipResponse> blockRelationship(
            @PathVariable String id,
            @RequestParam String blockerId
    ) {
        return ResponseEntity.ok(relationshipService.blockRelationship(id, blockerId));
    }

    @PostMapping("/block")
    public ResponseEntity<RelationshipResponse> blockRelationshipDirectly(
            @RequestParam String requesterId,
            @RequestParam String receiverId) {
        return ResponseEntity.ok(relationshipService.blockUserDirectly(requesterId, receiverId));
    }

    @GetMapping("/blocked/{userId}")
    public ResponseEntity<List<RelationshipResponse>> getBlockedUsers(@PathVariable String userId) {
        return ResponseEntity.ok(relationshipService.getBlockedUsers(userId));
    }

    @DeleteMapping("/{id}/unblock")
    public ResponseEntity<Void> unblockRelationship(@PathVariable String id) {
        relationshipService.unblockRelationship(id);
        return ResponseEntity.noContent().build();
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
    public ResponseEntity<List<RelationshipResponse>> getFriends(
            @PathVariable String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (page != null && size != null) {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            return ResponseEntity.ok(relationshipService.getFriends(userId, pageable));
        }
        return ResponseEntity.ok(relationshipService.getFriends(userId));
    }

    /* ─── Lời mời nhận được (PENDING) ────────────────────── */
    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<RelationshipResponse>> getPendingRequests(
            @PathVariable String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (page != null && size != null) {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            return ResponseEntity.ok(relationshipService.getPendingRequests(userId, pageable));
        }
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
