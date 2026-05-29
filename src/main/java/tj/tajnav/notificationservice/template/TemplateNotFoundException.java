package tj.tajnav.notificationservice.template;

import tj.tajnav.notificationservice.domain.ChannelType;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(String templateCode, ChannelType channelType) {
        super("Template not found: code=%s, channel=%s".formatted(templateCode, channelType));
    }
}