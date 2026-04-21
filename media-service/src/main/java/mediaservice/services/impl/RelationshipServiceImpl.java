package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import mediaservice.mappers.RelationshipMapper;
import mediaservice.models.Relationship;
import mediaservice.models.UserAccount;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RelationshipType;
import mediaservice.realtime.RelationshipRealtimePublisher;
import mediaservice.repositories.RelationshipRepository;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.RelationshipService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipServiceImpl implements RelationshipService {

    private final RelationshipRepository relationshipRepository;
    private final RelationshipMapper relationshipMapper;
    private final UserAccountRepository userAccountRepository;
    private final RelationshipRealtimePublisher relationshipRealtimePublisher;

    // ── CRUD cơ bản ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RelationshipResponse createRelationship(RelationshipRequest request) {
        Relationship relationship = buildRelationshipFromRequest(request);
        return relationshipMapper.toResponse(relationshipRepository.save(relationship));
    }

    @Override
    @Transactional(readOnly = true)
    public RelationshipResponse getRelationshipById(String id) {
        return relationshipMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getAllRelationships() {
        return relationshipMapper.toResponseList(relationshipRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelationshipResponse> getAllRelationships(Pageable pageable) {
        return relationshipRepository.findAll(pageable).map(relationshipMapper::toResponse);
    }

    @Override
    @Transactional
    public RelationshipResponse updateRelationship(String id, RelationshipRequest request) {
        Relationship relationship = findOrThrow(id);
        relationshipMapper.updateEntity(request, relationship);
        return relationshipMapper.toResponse(relationshipRepository.save(relationship));
    }

    @Override
    @Transactional
    public void deleteRelationship(String id) {
        if (!relationshipRepository.existsById(id)) {
            throw new RuntimeException("Relationship not found with id: " + id);
        }
        relationshipRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getRelationshipsByUserId(String userId) {
        return relationshipMapper.toResponseList(
                relationshipRepository.findFriendsByUserId(userId, RelationshipStatusType.ACCEPTED));
    }

    // ── Chức năng kết bạn ──────────────────────────────────────────────────

    @Override
    @Transactional
    public RelationshipResponse sendFriendRequest(String requesterId, String receiverId) {
        if (requesterId.equals(receiverId)) {
            throw new IllegalArgumentException("Không thể tự kết bạn với chính mình.");
        }

        relationshipRepository.findBetweenUsers(requesterId, receiverId).ifPresent(r -> {
            if (r.getStatus() == RelationshipStatusType.ACCEPTED) {
                throw new IllegalStateException("Đã tồn tại quan hệ giữa hai người dùng này.");
            }
        });

        UserAccount requester = findUserOrThrow(requesterId);
        UserAccount receiver = findUserOrThrow(receiverId);

        Relationship rel = relationshipRepository.findBetweenUsers(requesterId, receiverId)
                .orElse(new Relationship());

        rel.setRequester(requester);
        rel.setReceiver(receiver);
        rel.setStatus(RelationshipStatusType.PENDING);
        rel.setType(RelationshipType.FRIEND);

        Relationship saved = relationshipRepository.save(rel);
        relationshipRealtimePublisher.publishAfterCommit("REQUEST_SENT", saved, requesterId);
        return relationshipMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RelationshipResponse acceptFriendRequest(String relationshipId) {
        Relationship rel = findOrThrow(relationshipId);
        if (rel.getStatus() != RelationshipStatusType.PENDING) {
            throw new IllegalStateException("Lời mời không ở trạng thái chờ.");
        }
        rel.setStatus(RelationshipStatusType.ACCEPTED);
        rel.setAcceptedAt(LocalDateTime.now());

        Relationship saved = relationshipRepository.save(rel);
        String actorId = rel.getReceiver() != null ? rel.getReceiver().getId() : null;
        relationshipRealtimePublisher.publishAfterCommit("REQUEST_ACCEPTED", saved, actorId);
        return relationshipMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RelationshipResponse blockRelationship(String relationshipId, String blockerId) {
        Relationship rel = findOrThrow(relationshipId);
        UserAccount blocker = findUserOrThrow(blockerId);
        rel.setStatus(RelationshipStatusType.BLOCKED);
        rel.setBlockedBy(blocker);

        Relationship saved = relationshipRepository.save(rel);
        relationshipRealtimePublisher.publishAfterCommit("BLOCKED", saved, blockerId);
        return relationshipMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void rejectFriendRequest(String relationshipId) {
        Relationship rel = findOrThrow(relationshipId);
        if (rel.getStatus() != RelationshipStatusType.PENDING) {
            throw new IllegalStateException("Lời mời không ở trạng thái chờ.");
        }
        String actorId = rel.getReceiver() != null ? rel.getReceiver().getId() : null;
        relationshipRealtimePublisher.publishAfterCommit("REQUEST_REJECTED", rel, actorId);
        relationshipRepository.delete(rel);
    }

    @Override
    @Transactional
    public void cancelFriendRequest(String relationshipId) {
        Relationship rel = findOrThrow(relationshipId);
        if (rel.getStatus() != RelationshipStatusType.PENDING) {
            throw new IllegalStateException("Chỉ có thể hủy lời mời đang ở trạng thái chờ.");
        }
        String actorId = rel.getRequester() != null ? rel.getRequester().getId() : null;
        relationshipRealtimePublisher.publishAfterCommit("REQUEST_CANCELED", rel, actorId);
        relationshipRepository.delete(rel);
    }

    @Override
    @Transactional
    public void unfriend(String relationshipId) {
        Relationship rel = findOrThrow(relationshipId);
        if (rel.getStatus() != RelationshipStatusType.ACCEPTED) {
            throw new IllegalStateException("Hai người dùng này chưa là bạn bè.");
        }
        relationshipRealtimePublisher.publishAfterCommit("UNFRIENDED", rel, null);
        relationshipRepository.delete(rel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getFriends(String userId) {
        return relationshipMapper.toResponseList(
                relationshipRepository.findFriendsByUserId(userId, RelationshipStatusType.ACCEPTED));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getPendingRequests(String userId) {
        return relationshipMapper.toResponseList(
                relationshipRepository.findByReceiverIdAndStatus(userId, RelationshipStatusType.PENDING));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getSentRequests(String userId) {
        return relationshipMapper.toResponseList(
                relationshipRepository.findByRequesterIdAndStatus(userId, RelationshipStatusType.PENDING));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RelationshipResponse> getRelationshipBetween(String userId1, String userId2) {
        return relationshipRepository.findBetweenUsers(userId1, userId2)
                .map(relationshipMapper::toResponse);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Relationship findOrThrow(String id) {
        return relationshipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Relationship not found with id: " + id));
    }

    private UserAccount findUserOrThrow(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

    @Override
    @Transactional
    public void syncRelationshipFromEvent(String requesterId, String receiverId, String status) {
        log.info("[RelationshipSync] Syncing relationship: {} -> {} status={}", requesterId, receiverId, status);
        
        Optional<Relationship> existing = relationshipRepository.findBetweenUsers(requesterId, receiverId);
        
        if (status.equals("REMOVED")) {
            existing.ifPresent(relationshipRepository::delete);
            return;
        }

        Relationship rel = existing.orElse(new Relationship());
        
        UserAccount requester = userAccountRepository.findById(requesterId).orElse(null);
        UserAccount receiver = userAccountRepository.findById(receiverId).orElse(null);
        
        if (requester == null || receiver == null) {
            log.warn("[RelationshipSync] Missing users for sync: requester={}, receiver={}", requesterId, receiverId);
            return;
        }

        rel.setRequester(requester);
        rel.setReceiver(receiver);
        rel.setStatus(RelationshipStatusType.valueOf(status));
        rel.setType(RelationshipType.FRIEND);
        
        relationshipRepository.save(rel);
        // Do NOT call realtimePublisher.publish here as it might cause loops if both sides emit
        // However, we might want to emit to Socket.IO only
    }

    private Relationship buildRelationshipFromRequest(RelationshipRequest request) {
        Relationship rel = new Relationship();
        rel.setRequester(findUserOrThrow(request.getRequesterId()));
        rel.setReceiver(findUserOrThrow(request.getReceiverId()));
        rel.setStatus(request.getStatus());
        rel.setType(request.getType() != null ? request.getType() : RelationshipType.FRIEND);
        return rel;
    }
}
