package iuh.fit.userservice.utils;

import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ControllerUtils {

    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHENTICATED);
        String userId = auth.getName();
        if (userId == null || userId.equals("anonymousUser")) throw new AppException(ErrorCode.UNAUTHENTICATED);
        return userId;
    }

    public String getCurrentSessionToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() != null) return auth.getCredentials().toString();
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    public String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) return xForwardedFor.split(",")[0].trim();
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) return xRealIp;
        return request.getRemoteAddr();
    }

    public String getUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "Unknown";
    }

    public void enrichWithClientInfo(Object dto, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = getUserAgent(httpRequest);
        try {
            dto.getClass().getMethod("setIpAddress", String.class).invoke(dto, ip);
            try {
                var getDeviceInfo = dto.getClass().getMethod("getDeviceInfo");
                if (getDeviceInfo.invoke(dto) == null) {
                    dto.getClass().getMethod("setDeviceInfo", String.class).invoke(dto, ua);
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception ignored) {}
    }
}