package tj.tajnav.notificationservice.domain;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(NotificationStatus from, NotificationStatus to) {
        super("Invalid status transition: %s -> %s".formatted(from, to));
    }
}