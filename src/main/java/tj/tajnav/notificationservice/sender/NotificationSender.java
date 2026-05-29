package tj.tajnav.notificationservice.sender;

import tj.tajnav.notificationservice.domain.ChannelType;

public interface NotificationSender {
    ChannelType channel();
    void send(String recipientId, String renderedContent);
}