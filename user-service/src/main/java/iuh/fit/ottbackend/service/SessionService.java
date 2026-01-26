package iuh.fit.ottbackend.service;

import com.nimbusds.jwt.SignedJWT;
import iuh.fit.ottbackend.dto.response.SessionInfo;
import iuh.fit.ottbackend.dto.response.UserSessionsResponse;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.UserSession;
import iuh.fit.ottbackend.entity.enums.DeviceType;
import iuh.fit.ottbackend.entity.enums.LoginMethod;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.repository.UserSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final JwtService jwtService;
    private final EntityManager entityManager;

    @Transactional
    public UserSession createUserSession(User user, String deviceId, DeviceType deviceType,
                                         String deviceName, String ipAddress, String userAgent,
                                         String sessionToken, String refreshToken,
                                         LoginMethod loginMethod) {

        // CRITICAL FIX: DELETE old session instead of UPDATE to avoid duplicate key error
        if (deviceId != null && user != null) {
            Optional<UserSession> existingSession = userSessionRepository
                    .findByDeviceIdAndUserAndIsActive(deviceId, user, true);

            if (existingSession.isPresent()) {
                UserSession oldSession = existingSession.get();

                // DELETE instead of marking as inactive to avoid unique constraint violation
                userSessionRepository.delete(oldSession);
                entityManager.flush(); // Force delete to DB immediately

                log.info("Deleted old session for deviceId: {}, userId: {}", deviceId, user.getId());
            }
        }

        // Now create new session - old one is deleted from DB
        UserSession session = UserSession.builder()
                .user(user)
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

        return userSessionRepository.save(session);
    }

    public UserSessionsResponse getUserSessions(String userId, String currentToken) {
        List<UserSession> sessions = userSessionRepository
                .findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(userId);

        List<SessionInfo> sessionInfos = sessions.stream()
                .map(session -> toSessionInfo(session, currentToken))
                .collect(Collectors.toList());

        return UserSessionsResponse.builder()
                .sessions(sessionInfos)
                .total(sessionInfos.size())
                .build();
    }

    /**
     * Revoke session by sessionId (UUID)
     */
    @Transactional
    public void revokeSession(String userId, String sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!session.getIsActive()) {
            return;
        }

        // Mark as inactive and save
        session.revoke("Revoked by user");
        userSessionRepository.save(session);

        invalidateSessionTokens(session);
    }

    /**
     * Revoke session by deviceId - for logout
     */
    @Transactional
    public void revokeSessionByDevice(String userId, String deviceId) {
        if (deviceId == null) {
            return;
        }

        User user = User.builder().id(userId).build();
        Optional<UserSession> sessionOpt = userSessionRepository
                .findByDeviceIdAndUserAndIsActive(deviceId, user, true);

        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();

            // DELETE instead of marking inactive to avoid duplicate key issues
            invalidateSessionTokens(session);
            userSessionRepository.delete(session);
            entityManager.flush();

            log.info("Deleted session for deviceId: {}, userId: {}", deviceId, userId);
        }
    }

    @Transactional
    public void revokeAllOtherSessions(String userId, String currentSessionToken) {
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);

        int revokedCount = 0;
        for (UserSession session : sessions) {
            if (!session.getSessionToken().equals(currentSessionToken)) {
                session.revoke("Revoked by user - all other sessions");
                invalidateSessionTokens(session);
                revokedCount++;
            }
        }

        if (revokedCount > 0) {
            userSessionRepository.saveAll(sessions);
        }
    }

    @Transactional
    public int revokeAllUserSessions(String userId, String reason) {
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);

        if (sessions.isEmpty()) {
            return 0;
        }

        sessions.forEach(session -> {
            session.revoke(reason);
            invalidateSessionTokens(session);
        });

        userSessionRepository.saveAll(sessions);
        return sessions.size();
    }

    public UserSession findActiveSessionByDeviceAndUser(String deviceId, User user) {
        return userSessionRepository
                .findByDeviceIdAndUserAndIsActive(deviceId, user, true)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
    }

    @Transactional
    public void updateSessionTokens(String deviceId, User user, String newToken, String newRefreshToken) {
        userSessionRepository.findByDeviceIdAndUser(deviceId, user)
                .ifPresent(session -> {
                    if (!session.getIsActive()) {
                        return;
                    }

                    session.setSessionToken(newToken);
                    session.setRefreshToken(newRefreshToken);
                    session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getExpiration()));
                    session.setRefreshExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration()));
                    session.setLastActiveAt(LocalDateTime.now());
                    userSessionRepository.save(session);
                });
    }

    private void invalidateSessionTokens(UserSession session) {
        try {
            User user = session.getUser();

            if (session.getSessionToken() != null) {
                try {
                    SignedJWT signedJWT = SignedJWT.parse(session.getSessionToken());
                    String jwtId = signedJWT.getJWTClaimsSet().getJWTID();

                    jwtService.invalidateToken(
                            jwtId,
                            session.getExpiresAt(),
                            user,
                            "ACCESS",
                            "Session revoked"
                    );
                } catch (ParseException e) {
                    // Ignore parse errors
                }
            }

            if (session.getRefreshToken() != null) {
                String refreshTokenId = "refresh_" + session.getId();

                jwtService.invalidateToken(
                        refreshTokenId,
                        session.getRefreshExpiresAt(),
                        user,
                        "REFRESH",
                        "Session revoked"
                );
            }

        } catch (Exception e) {
            // Log error but don't throw
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
}