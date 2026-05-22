package iuh.fit.authservice.service;

import com.nimbusds.jwt.SignedJWT;
import iuh.fit.authservice.dto.response.SessionInfo;
import iuh.fit.authservice.dto.response.UserSessionsResponse;
import iuh.fit.authservice.entity.User;
import iuh.fit.authservice.entity.UserSession;
import iuh.fit.authservice.entity.enums.DeviceType;
import iuh.fit.authservice.entity.enums.LoginMethod;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import iuh.fit.authservice.repository.QrLoginSessionRepository;
import iuh.fit.authservice.repository.UserSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final QrLoginSessionRepository qrLoginSessionRepository;
    private final JwtService jwtService;
    private final EntityManager entityManager;
    private final NotificationPublisher notificationPublisher;

    @Value("${jwt.expiration:3600}")
    private long jwtExpiration;

    @Transactional
    public UserSession createUserSession(String userId, String deviceId, DeviceType deviceType,
                                         String deviceName, String ipAddress, String userAgent,
                                         String sessionToken, String refreshToken,
                                         LoginMethod loginMethod) {

        log.info("Creating new session for userId: {}, deviceId: {}, loginMethod: {}",
                userId, deviceId, loginMethod);

        revokeSessionsInSameDeviceSlot(userId, deviceId, deviceType);

        UserSession session = UserSession.builder()
                .userId(userId)
                .sessionToken(sessionToken)
                .refreshToken(refreshToken)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginMethod(loginMethod)
                .isActive(true)
                .twoFactorVerified(false)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getExpiration()))
                .refreshExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration()))
                .build();

        UserSession savedSession = userSessionRepository.save(session);
        log.info("Session created successfully - sessionId: {}, userId: {}", savedSession.getId(), userId);

        return savedSession;
    }

    private void revokeSessionsInSameDeviceSlot(String userId, String currentDeviceId, DeviceType currentDeviceType) {
        if (userId == null) return;

        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeSessions.isEmpty()) return;

        List<UserSession> revokedSessions = new ArrayList<>();
        List<String> revokedOtherDeviceIds = new ArrayList<>();

        for (UserSession session : activeSessions) {
            if (!isSameDeviceSlot(session.getDeviceType(), currentDeviceType)) {
                continue;
            }

            session.revoke("Replaced by new login on same device slot");
            invalidateSessionTokens(session);
            qrLoginSessionRepository.nullifySessionReference(session);
            revokedSessions.add(session);

            boolean samePhysicalDevice = currentDeviceId != null
                    && currentDeviceId.equals(session.getDeviceId());
            if (!samePhysicalDevice && session.getDeviceId() != null) {
                revokedOtherDeviceIds.add(session.getDeviceId());
            }
        }

        if (revokedSessions.isEmpty()) return;

        userSessionRepository.saveAll(revokedSessions);
        entityManager.flush();

        if (!revokedOtherDeviceIds.isEmpty()) {
            notificationPublisher.publishUserLogoutEvent(userId, null, null, "OTHERS", revokedOtherDeviceIds);
        }

        log.info("Revoked {} auth sessions in same device slot for userId: {}",
                revokedSessions.size(), userId);
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

        List<UserSession> sessions = userSessionRepository
                .findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(userId);

        List<SessionInfo> sessionInfos = sessions.stream()
                .map(session -> toSessionInfo(session, currentToken))
                .collect(Collectors.toList());

        log.info("Retrieved {} active sessions for userId: {}", sessionInfos.size(), userId);

        return UserSessionsResponse.builder()
                .sessions(sessionInfos)
                .total(sessionInfos.size())
                .build();
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        log.info("Revoking session - sessionId: {}, userId: {}", sessionId, userId);

        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUserId().equals(userId)) {
            log.warn("Unauthorized revoke attempt on sessionId: {}", sessionId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!session.getIsActive()) {
            log.debug("Session already inactive - sessionId: {}", sessionId);
            return;
        }

        session.revoke("Revoked by user");
        userSessionRepository.save(session);
        invalidateSessionTokens(session);

        notificationPublisher.publishUserLogoutEvent(userId, sessionId, session.getDeviceId(), "SPECIFIC", null);

        log.info("Session revoked successfully - sessionId: {}", sessionId);
    }

    @Transactional
    public void revokeSessionByDevice(String userId, String deviceId) {
        if (deviceId == null) return;

        log.info("Revoking session by deviceId: {}, userId: {}", deviceId, userId);

        Optional<UserSession> sessionOpt = userSessionRepository
                .findByDeviceIdAndUserIdAndIsActive(deviceId, userId, true);

        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            invalidateSessionTokens(session);
            qrLoginSessionRepository.nullifySessionReference(session);
            entityManager.flush();
            userSessionRepository.delete(session);
            entityManager.flush();

            notificationPublisher.publishUserLogoutEvent(userId, null, deviceId, "SPECIFIC", null);

            log.info("Session deleted by deviceId: {}, userId: {}", deviceId, userId);
        } else {
            log.debug("No active session found for deviceId: {}", deviceId);
        }
    }

    @Transactional
    public void revokeAllOtherSessions(String userId, String currentSessionToken) {
        log.info("Revoking all other sessions for userId: {}", userId);

        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);

        int revokedCount = 0;
        java.util.List<String> revokedDeviceIds = new java.util.ArrayList<>();
        for (UserSession session : sessions) {
            if (!session.getSessionToken().equals(currentSessionToken)) {
                session.revoke("Revoked by user - all other sessions");
                invalidateSessionTokens(session);
                revokedDeviceIds.add(session.getDeviceId());
                revokedCount++;
            }
        }

        if (revokedCount > 0) {
            userSessionRepository.saveAll(sessions);
            notificationPublisher.publishUserLogoutEvent(userId, null, null, "OTHERS", revokedDeviceIds);
            log.info("Revoked {} other sessions for userId: {}", revokedCount, userId);
        } else {
            log.debug("No other sessions to revoke for userId: {}", userId);
        }
    }

    public int revokeAllUserSessions(String userId, String reason) {
        log.info("Internal revoke-all called for userId: {} - auth-service will delete all active sessions", userId);
        
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);
        if (sessions.isEmpty()) return 0;
        
        for (UserSession session : sessions) {
            session.revoke(reason);
            invalidateSessionTokens(session);
        }
        userSessionRepository.saveAll(sessions);
        
        notificationPublisher.publishUserLogoutEvent(userId, null, null, "ALL", null);
        
        return sessions.size();
    }

    @Transactional
    public void updateSessionTokens(String deviceId, String userId, String newToken, String newRefreshToken) {
        log.debug("Updating tokens for deviceId: {}, userId: {}", deviceId, userId);

        userSessionRepository.findByDeviceIdAndUserId(deviceId, userId)
                .ifPresent(session -> {
                    if (!session.getIsActive()) return;

                    session.setSessionToken(newToken);
                    session.setRefreshToken(newRefreshToken);
                    session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getExpiration()));
                    session.setRefreshExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration()));
                    session.setLastActiveAt(LocalDateTime.now());
                    userSessionRepository.save(session);

                    log.info("Session tokens updated successfully for deviceId: {}", deviceId);
                });
    }

    private void invalidateSessionTokens(UserSession session) {
        log.debug("Invalidating tokens for sessionId: {}", session.getId());

        try {
            if (session.getSessionToken() != null) {
                try {
                    SignedJWT signedJWT = SignedJWT.parse(session.getSessionToken());
                    String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
                    jwtService.invalidateToken(jwtId, session.getExpiresAt(),
                            session.getUserId(), "ACCESS", "Session revoked");
                } catch (ParseException e) {
                    log.warn("Failed to parse session token for invalidation");
                }
            }

            if (session.getRefreshToken() != null) {
                String refreshTokenId = "refresh_" + session.getId();
                jwtService.invalidateToken(refreshTokenId, session.getRefreshExpiresAt(),
                        session.getUserId(), "REFRESH", "Session revoked");
            }
        } catch (Exception e) {
            log.error("Error while invalidating tokens for sessionId: {}", session.getId(), e);
        }
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

    public void invalidateTokenById(String jwtId, String reason) {
        log.info("Invalidating token by jwtId: {}", jwtId);

        LocalDateTime expiry = LocalDateTime.now().plusSeconds(jwtExpiration);

        jwtService.invalidateToken(jwtId, expiry, null, "ACCESS", reason);

        log.info("Token invalidated - jwtId: {}", jwtId);
    }

    public Optional<UserSession> findActiveSessionByRefreshToken(String refreshToken) {
        return userSessionRepository.findByRefreshTokenAndIsActive(refreshToken, true);
    }

    @Transactional
    public void updateSessionTokensByRefreshToken(UserSession session, String newToken, String newRefreshToken) {
        session.setSessionToken(newToken);
        session.setRefreshToken(newRefreshToken);
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getExpiration()));
        session.setRefreshExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration()));
        session.setLastActiveAt(LocalDateTime.now());
        userSessionRepository.save(session);
        log.info("Session tokens updated via refresh for userId: {}", session.getUserId());
    }
}
