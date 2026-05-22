package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.response.SessionInfo;
import iuh.fit.userservice.dto.response.UserSessionsResponse;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.UserSession;
import iuh.fit.userservice.entity.enums.DeviceType;
import iuh.fit.userservice.entity.enums.LoginMethod;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final AuthSessionClient authSessionClient;
    private final UserEventPublisher userEventPublisher;

    @Value("${jwt.expiration:3600}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:86400}")
    private long refreshExpiration;

    @Transactional
    public UserSession createUserSession(User user, String deviceId, DeviceType deviceType,
                                         String deviceName, String ipAddress, String deviceInfo,
                                         String sessionToken, String refreshToken, LoginMethod loginMethod) {

        log.info("Creating new session for userId: {} | DeviceId: {} | LoginMethod: {}",
                user.getId(), deviceId, loginMethod);

        revokeSessionsInSameDeviceSlot(user, deviceId, deviceType);

        UserSession session = UserSession.builder()
                .user(user)
                .sessionToken(sessionToken)
                .refreshToken(refreshToken)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .userAgent(deviceInfo)
                .loginMethod(loginMethod)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtExpiration))
                .refreshExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiration))
                .build();

        UserSession savedSession = userSessionRepository.save(session);
        log.info("Session created successfully - sessionId: {}, userId: {}, deviceId: {}",
                savedSession.getId(), user.getId(), deviceId);

        return savedSession;
    }

    private void revokeSessionsInSameDeviceSlot(User user, String currentDeviceId, DeviceType currentDeviceType) {
        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsActiveTrue(user.getId());
        if (activeSessions.isEmpty()) return;

        List<UserSession> revokedSessions = new ArrayList<>();
        List<String> revokedOtherDeviceIds = new ArrayList<>();

        for (UserSession session : activeSessions) {
            if (!isSameDeviceSlot(session.getDeviceType(), currentDeviceType)) {
                continue;
            }

            session.revoke("Replaced by new login on same device slot");
            authSessionClient.revokeSession(session.getSessionToken(), user.getId());
            revokedSessions.add(session);

            boolean samePhysicalDevice = currentDeviceId != null
                    && currentDeviceId.equals(session.getDeviceId());
            if (!samePhysicalDevice && session.getDeviceId() != null) {
                revokedOtherDeviceIds.add(session.getDeviceId());
            }
        }

        if (revokedSessions.isEmpty()) return;

        userSessionRepository.saveAll(revokedSessions);

        if (!revokedOtherDeviceIds.isEmpty()) {
            userEventPublisher.publishUserLogout(
                    iuh.fit.userservice.dto.event.UserLogoutEvent.builder()
                            .userId(user.getId())
                            .action("OTHERS")
                            .revokedDeviceIds(revokedOtherDeviceIds)
                            .build()
            );
        }

        log.info("Revoked {} user-service sessions in same device slot for userId: {}",
                revokedSessions.size(), user.getId());
    }

    private boolean isSameDeviceSlot(DeviceType existingType, DeviceType incomingType) {
        return normalizeDeviceSlot(existingType).equals(normalizeDeviceSlot(incomingType));
    }

    private String normalizeDeviceSlot(DeviceType deviceType) {
        if (deviceType == DeviceType.MOBILE || deviceType == DeviceType.TABLET) {
            return "MOBILE";
        }
        return "COMPUTER";
    }

    public UserSessionsResponse getUserSessions(String userId, String currentToken) {
        log.debug("Fetching active sessions for userId: {}", userId);

        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(userId);

        List<SessionInfo> sessionInfos = sessions.stream()
                .map(s -> toSessionInfo(s, currentToken))
                .toList();

        log.info("Retrieved {} active sessions for userId: {}", sessionInfos.size(), userId);

        return UserSessionsResponse.builder()
                .sessions(sessionInfos)
                .total(sessionInfos.size())
                .build();
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        session.revoke("Revoked by user");
        userSessionRepository.save(session);

        authSessionClient.revokeSession(session.getSessionToken(), userId);

        userEventPublisher.publishUserLogout(
                iuh.fit.userservice.dto.event.UserLogoutEvent.builder()
                        .userId(userId)
                        .sessionId(sessionId)
                        .deviceId(session.getDeviceId())
                        .action("SPECIFIC")
                        .build()
        );

        log.info("Session revoked successfully - sessionId: {}", sessionId);
    }

    @Transactional
    public int revokeAllUserSessions(String userId, String reason) {
        log.info("Revoking all sessions for userId: {} | Reason: {}", userId, reason);

        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);

        if (sessions.isEmpty()) {
            log.debug("No active sessions found for userId: {}", userId);
            return 0;
        }

        sessions.forEach(s -> s.revoke(reason));
        userSessionRepository.saveAll(sessions);

        authSessionClient.revokeAllSessions(userId, reason);

        userEventPublisher.publishUserLogout(
                iuh.fit.userservice.dto.event.UserLogoutEvent.builder()
                        .userId(userId)
                        .action("ALL")
                        .build()
        );

        log.info("Successfully revoked {} sessions for userId: {}", sessions.size(), userId);
        return sessions.size();
    }

    @Transactional
    public void revokeAllOtherSessions(String userId, String currentToken) {
        log.info("Revoking all other sessions for userId: {}", userId);

        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(userId);

        int revokedCount = 0;
        for (UserSession session : sessions) {
            if (!session.getSessionToken().equals(currentToken)) {
                session.revoke("Revoked by user - logout other devices");
                revokedCount++;
            }
        }

        if (revokedCount > 0) {
            userSessionRepository.saveAll(sessions);

            java.util.List<String> revokedDeviceIds = new java.util.ArrayList<>();
            for (UserSession session : sessions) {
                if (!session.getSessionToken().equals(currentToken)) {
                    revokedDeviceIds.add(session.getDeviceId());
                }
            }

            userEventPublisher.publishUserLogout(
                    iuh.fit.userservice.dto.event.UserLogoutEvent.builder()
                            .userId(userId)
                            .action("OTHERS")
                            .revokedDeviceIds(revokedDeviceIds)
                            .build()
            );

            log.info("Revoked {} other sessions for userId: {}", revokedCount, userId);
        } else {
            log.debug("No other sessions to revoke for userId: {}", userId);
        }
    }



    @Transactional
    public void updateSessionTokens(String deviceId, User user, String newToken, String newRefreshToken) {
        log.debug("Updating session tokens for deviceId: {}, userId: {}", deviceId, user.getId());

        userSessionRepository.findByDeviceIdAndUserAndIsActive(deviceId, user, true)
                .ifPresent(session -> {
                    session.setSessionToken(newToken);
                    session.setRefreshToken(newRefreshToken);
                    session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpiration));
                    session.setRefreshExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiration));
                    userSessionRepository.save(session);

                    log.info("Session tokens updated successfully for deviceId: {}", deviceId);
                });
    }

    private SessionInfo toSessionInfo(UserSession session, String currentToken) {
        boolean isCurrent = session.getSessionToken() != null
                && session.getSessionToken().equals(currentToken);

        return SessionInfo.builder()
                .id(session.getId())
                .deviceId(session.getDeviceId())
                .deviceType(session.getDeviceType())
                .deviceName(session.getDeviceName())
                .ipAddress(session.getIpAddress())
                .location(session.getLocation())
                .loginMethod(session.getLoginMethod())
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .expiresAt(session.getExpiresAt())
                .isActive(session.getIsActive())
                .isCurrent(isCurrent)
                .twoFactorVerified(session.getTwoFactorVerified())
                .build();
    }

}
