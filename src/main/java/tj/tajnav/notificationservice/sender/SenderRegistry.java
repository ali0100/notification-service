package tj.tajnav.notificationservice.sender;

import org.springframework.stereotype.Component;
import tj.tajnav.notificationservice.domain.ChannelType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SenderRegistry {

    private final Map<ChannelType, NotificationSender> senders;

    public SenderRegistry(List<NotificationSender> senderList) {
        this.senders = senderList.stream()
            .collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
    }

    public NotificationSender get(ChannelType channelType) {
        NotificationSender sender = senders.get(channelType);
        if (sender == null) {
            throw new IllegalStateException("No sender registered for channel: " + channelType);
        }
        return sender;
    }
}