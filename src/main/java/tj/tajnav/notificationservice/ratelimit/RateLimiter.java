package tj.tajnav.notificationservice.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final int MAX_PER_MINUTE = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public void checkAndIncrement(UUID recipientId) {
        String key = "ratelimit:" + recipientId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count > MAX_PER_MINUTE) {
            throw new RateLimitExceededException(recipientId);
        }
    }
}