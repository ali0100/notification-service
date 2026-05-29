package tj.tajnav.notificationservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tj.tajnav.notificationservice.domain.ChannelType;
import tj.tajnav.notificationservice.domain.Priority;

import java.util.Map;
import java.util.UUID;

@Data
public class NotificationRequest {

    @NotNull
    private UUID recipientId;

    @NotBlank
    private String templateCode;

    @NotNull
    private ChannelType channelType;

    private Map<String, Object> parameters;

    @NotNull
    private Priority priority;
}