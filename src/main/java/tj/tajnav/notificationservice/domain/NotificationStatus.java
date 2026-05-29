package tj.tajnav.notificationservice.domain;

import java.util.Set;
import java.util.Map;

public enum NotificationStatus {
    ACCEPTED, PROCESSING, SENT, FAILED;

    private static final Map<NotificationStatus, Set<NotificationStatus>> ALLOWED = Map.of(
        ACCEPTED,   Set.of(PROCESSING),
        PROCESSING, Set.of(SENT, FAILED)
    );

    public void validateTransitionTo(NotificationStatus next) {
        Set<NotificationStatus> allowed = ALLOWED.getOrDefault(this, Set.of());
        if (!allowed.contains(next)) {
            throw new InvalidStatusTransitionException(this, next);
        }
    }
}