package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.enums.UserStatusAction;
import iuh.fit.userservice.dto.event.UserStatusChangedEvent;
import iuh.fit.userservice.dto.event.UserStatusSnapshot;
import iuh.fit.userservice.dto.response.AdminUserStatusResponse;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserStatusService {

    private final UserRepository userRepository;
    private final OutboxEventService outboxEventService;

    @Transactional
    public AdminUserStatusResponse updateStatus(
            String targetUserId,
            UserStatusAction actionType,
            String reason,
            Long durationMinutes,
            Boolean isPermanent,
            String actorId,
            String actorRole) {

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean actorIsModerator = "MODERATOR".equalsIgnoreCase(actorRole)
                && !"SUPER_ADMIN".equalsIgnoreCase(actorRole);
        boolean targetIsSuperAdmin = user.getAccountType() == AccountType.ADMIN;

        if (actorIsModerator && targetIsSuperAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        UserStatusSnapshot previousStatus = snapshot(user);

        if (user.getDeletedAt() != null && actionType != UserStatusAction.RESTORE) {
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        switch (actionType) {
            case BLOCK -> applyBlock(user, reason, durationMinutes, isPermanent);
            case UNBLOCK -> applyUnblock(user);
            case SOFT_DELETE -> applySoftDelete(user);
            case RESTORE -> applyRestore(user);
            default -> throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        User saved = userRepository.save(user);

        UserStatusSnapshot newStatus = snapshot(saved);
        UserStatusChangedEvent event = UserStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(saved.getId())
                .actionType(actionType)
                .actorId(actorId)
                .actorRole(actorRole)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .reason(reason)
                .effectiveUntil(saved.getBlockedUntil())
                .timestamp(Instant.now())
                .build();

        outboxEventService.enqueueUserStatusChanged(event);

        return AdminUserStatusResponse.builder()
                .userId(saved.getId())
                .accountType(saved.getAccountType())
                .isActive(saved.getIsActive())
                .isBlocked(saved.getIsBlocked())
                .blockedUntil(saved.getBlockedUntil())
                .blockedReason(saved.getBlockedReason())
                .deletedAt(saved.getDeletedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private void applyBlock(User user, String reason, Long durationMinutes, Boolean isPermanent) {
        if (reason == null || reason.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (Boolean.TRUE.equals(isPermanent) && durationMinutes != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        user.setIsBlocked(true);
        user.setBlockedReason(reason);
        if (Boolean.TRUE.equals(isPermanent)) {
            user.setBlockedUntil(null);
            return;
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        user.setBlockedUntil(LocalDateTime.now().plusMinutes(durationMinutes));
    }

    private void applyUnblock(User user) {
        user.setIsBlocked(false);
        user.setBlockedUntil(null);
        user.setBlockedReason(null);
    }

    private void applySoftDelete(User user) {
        user.setDeletedAt(LocalDateTime.now());
        user.setIsActive(false);
        user.setIsBlocked(false);
        user.setBlockedUntil(null);
        user.setBlockedReason(null);
    }

    private void applyRestore(User user) {
        user.setDeletedAt(null);
        user.setIsActive(true);
        user.setIsBlocked(false);
        user.setBlockedUntil(null);
        user.setBlockedReason(null);
    }

    private UserStatusSnapshot snapshot(User user) {
        return UserStatusSnapshot.builder()
                .isActive(user.getIsActive())
                .isBlocked(user.getIsBlocked())
                .blockedUntil(user.getBlockedUntil())
                .blockedReason(user.getBlockedReason())
                .deletedAt(user.getDeletedAt())
                .build();
    }

}
