package iuh.fit.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${otp.cache.ttl:300}")
    private long otpTtlSeconds;

    @Value("${otp.rate-limit.max-per-hour:5}")
    private int maxOtpPerHour;

    private String otpKey(String identity, String otpType) {
        return "otp:" + otpType + ":" + identity;
    }

    private String rateLimitKey(String identity, String otpType) {
        return "otp:rate:" + otpType + ":" + identity;
    }

    public void saveOtp(String identity, String otpType, String otpCode) {
        String key = otpKey(identity, otpType);
        log.debug("Saving OTP to Redis cache - key: {}, ttl: {}s", key, otpTtlSeconds);

        redisTemplate.opsForValue().set(key, otpCode, otpTtlSeconds, TimeUnit.SECONDS);

        log.info("OTP cached successfully - identity: {}, type: {}", identity, otpType);
    }

    public boolean validateOtp(String identity, String otpType, String inputCode) {
        String key = otpKey(identity, otpType);
        log.debug("Validating OTP - key: {}", key);

        Object cached = redisTemplate.opsForValue().get(key);

        if (cached == null) {
            log.warn("OTP not found or expired in cache - identity: {}, type: {}", identity, otpType);
            return false;
        }

        boolean isValid = cached.toString().equals(inputCode);

        if (isValid) {
            log.info("OTP validation successful - identity: {}, type: {}", identity, otpType);
        } else {
            log.warn("Invalid OTP provided - identity: {}, type: {}", identity, otpType);
        }

        return isValid;
    }

    public void deleteOtp(String identity, String otpType) {
        String key = otpKey(identity, otpType);
        log.debug("Deleting OTP from cache - key: {}", key);

        redisTemplate.delete(key);

        log.debug("OTP deleted successfully - identity: {}, type: {}", identity, otpType);
    }

    public boolean isRateLimited(String identity, String otpType) {
        String key = rateLimitKey(identity, otpType);
        log.debug("Checking rate limit - identity: {}, type: {}", identity, otpType);

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
            log.debug("Rate limit counter initialized for 1 hour");
        }

        if (count > maxOtpPerHour) {
            log.warn("RATE LIMIT EXCEEDED - identity: {}, type: {}, attempts: {}/{}",
                    identity, otpType, count, maxOtpPerHour);
            return true;
        }

        log.debug("Rate limit check passed - attempts: {}/{}", count, maxOtpPerHour);
        return false;
    }

    public long getOtpTtl(String identity, String otpType) {
        String key = otpKey(identity, otpType);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        log.debug("OTP TTL check - key: {}, remaining: {}s", key, ttl);
        return ttl != null ? ttl : -1;
    }
}