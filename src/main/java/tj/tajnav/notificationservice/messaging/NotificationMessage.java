package tj.tajnav.notificationservice.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tj.tajnav.notificationservice.domain.ChannelType;
import tj.tajnav.notificationservice.domain.Priority;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private UUID notificationId;
    private UUID recipientId;
    private String templateCode;
    private ChannelType channelType;
    private Map<String, Object> parameters;
    private Priority priority;
}