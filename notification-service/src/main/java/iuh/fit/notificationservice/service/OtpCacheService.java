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
        redisTemplate.opsForValue().set(key, otpCode, otpTtlSeconds, TimeUnit.SECONDS);
        log.debug(" OTP cached: key={}", key);
    }

    public boolean validateOtp(String identity, String otpType, String inputCode) {
        String key = otpKey(identity, otpType);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            log.warn(" OTP expired or not found: key={}", key);
            return false;
        }
        return cached.toString().equals(inputCode);
    }

    public void deleteOtp(String identity, String otpType) {
        redisTemplate.delete(otpKey(identity, otpType));
    }

    public boolean isRateLimited(String identity, String otpType) {
        String key = rateLimitKey(identity, otpType);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
        if (count > maxOtpPerHour) {
            log.warn(" Rate limit exceeded: identity={}, type={}", identity, otpType);
            return true;
        }
        return false;
    }

    public long getOtpTtl(String identity, String otpType) {
        return redisTemplate.getExpire(otpKey(identity, otpType), TimeUnit.SECONDS);
    }
}
