package tj.tajnav.notificationservice.ratelimit;

import java.util.UUID;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(UUID recipientId) {
        super("Rate limit exceeded for recipientId: " + recipientId);
    }
}