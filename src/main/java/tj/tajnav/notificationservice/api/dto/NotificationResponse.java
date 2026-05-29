package tj.tajnav.notificationservice.api.dto;

import lombok.Data;
import tj.tajnav.notificationservice.domain.NotificationStatus;

import java.util.UUID;

@Data
public class NotificationResponse {
    private UUID notificationId;
    private NotificationStatus status;

    public NotificationResponse(UUID notificationId, NotificationStatus status) {
        this.notificationId = notificationId;
        this.status = status;
    }
}