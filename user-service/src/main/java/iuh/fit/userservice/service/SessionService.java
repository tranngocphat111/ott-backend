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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository userSessionRepository;

    @Value("${jwt.expiration:3600}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:86400}")
    private long refreshExpiration;

    @Transactional
    public UserSession createUserSession(User user, String deviceId, DeviceType deviceType,
                                         String deviceName, String ipAddress, String deviceInfo,
                                         String sessionToken, String refreshToken, LoginMethod loginMethod) {
        if (deviceId != null) {
            userSessionRepository.findByDeviceIdAndUserAndIsActive(deviceId, user, true)
                    .ifPresent(existing -> {
                        existing.revoke("New login from same device");
                        userSessionRepository.save(existing);
                    });
        }

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

        return userSessionRepository.save(session);
    }

    public UserSessionsResponse getUserSessions(String userId, String currentToken) {
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(userId);
        List<SessionInfo> sessionInfos = sessions.stream()
                .map(s -> toSessionInfo(s, currentToken))
                .toList();
        return UserSessionsResponse.builder().sessions(sessionInfos).total(sessionInfos.size()).build();
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
    }

    @Transactional
    public int revokeAllUserSessions(String userId, String reason) {
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrue(userId);
        sessions.forEach(s -> s.revoke(reason));
        userSessionRepository.saveAll(sessions);
        log.info("Revoked {} sessions for userId: {}", sessions.size(), userId);
        return sessions.size();
    }

    @Transactional
    public void revokeAllOtherSessions(String userId, String currentToken) {
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(userId);
        sessions.stream()
                .filter(s -> !s.getSessionToken().equals(currentToken))
                .forEach(s -> s.revoke("Revoked by user - logout other devices"));
        userSessionRepository.saveAll(sessions);
    }

    @Transactional
    public void revokeSessionByDevice(String userId, String deviceId) {
        userSessionRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(s -> deviceId.equals(s.getDeviceId()))
                .forEach(s -> {
                    s.revoke("Device logout");
                    userSessionRepository.save(s);
                });
    }

    @Transactional
    public void updateSessionTokens(String deviceId, User user, String newToken, String newRefreshToken) {
        userSessionRepository.findByDeviceIdAndUserAndIsActive(deviceId, user, true)
                .ifPresent(session -> {
                    session.setSessionToken(newToken);
                    session.setRefreshToken(newRefreshToken);
                    session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpiration));
                    session.setRefreshExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiration));
                    userSessionRepository.save(session);
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