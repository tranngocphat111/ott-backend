package iuh.fit.authservice.utils;

import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class ControllerUtils {

    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("userId");
            if (userId == null) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            return userId;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    public void enrichWithClientInfo(Object request, HttpServletRequest httpRequest) {
        try {
            String ipAddress = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            setFieldIfNull(request, "setIpAddress", ipAddress);
            setFieldIfNull(request, "setDeviceInfo", userAgent);
        } catch (Exception ignored) {
        }
    }

    private void setFieldIfNull(Object request, String setterName, String value) {
        try {
            Method getter = request.getClass().getMethod(setterName.replace("set", "get"));
            Object currentValue = getter.invoke(request);
            if (currentValue == null && value != null) {
                Method setter = request.getClass().getMethod(setterName, String.class);
                setter.invoke(request, value);
            }
        } catch (Exception ignored) {
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}