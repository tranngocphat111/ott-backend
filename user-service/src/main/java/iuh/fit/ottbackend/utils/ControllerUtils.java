package iuh.fit.ottbackend.utils;

import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ControllerUtils {

    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String userId = authentication.getName();
        if (userId == null || userId.equals("anonymousUser")) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userId;
    }

    public String getCurrentSessionToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getCredentials() != null) {
            return authentication.getCredentials().toString();
        }

        throw new AppException(ErrorCode.INVALID_TOKEN);
    }

    public String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }
        return request.getRemoteAddr();
    }

    public String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }

    public void enrichWithClientInfo(Object requestDto, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);

        try {
            java.lang.reflect.Method setIpAddress = requestDto.getClass().getMethod("setIpAddress", String.class);
            setIpAddress.invoke(requestDto, ipAddress);

            try {
                java.lang.reflect.Method setDeviceInfo = requestDto.getClass().getMethod("setDeviceInfo", String.class);
                Object currentDeviceInfo = requestDto.getClass().getMethod("getDeviceInfo").invoke(requestDto);
                if (currentDeviceInfo == null) {
                    setDeviceInfo.invoke(requestDto, userAgent);
                }
            } catch (NoSuchMethodException e) {
            }

        } catch (Exception e) {
        }
    }
}