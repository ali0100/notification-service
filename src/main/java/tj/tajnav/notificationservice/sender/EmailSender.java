package tj.tajnav.notificationservice.sender;

import org.springframework.stereotype.Component;
import tj.tajnav.notificationservice.domain.ChannelType;

@Component
class EmailSender extends AbstractStubSender {
    @Override
    public ChannelType channel() { return ChannelType.EMAIL; }
}