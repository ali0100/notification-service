package tj.tajnav.notificationservice.application;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.tajnav.notificationservice.api.dto.NotificationRequest;
import tj.tajnav.notificationservice.api.dto.NotificationResponse;
import tj.tajnav.notificationservice.domain.NotificationStatus;
import tj.tajnav.notificationservice.messaging.NotificationMessage;
import tj.tajnav.notificationservice.persistence.NotificationEntity;
import tj.tajnav.notificationservice.persistence.NotificationRepository;
import tj.tajnav.notificationservice.ratelimit.RateLimiter;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String TOPIC = "notification.requests";

    private final NotificationRepository repository;
    private final KafkaTemplate<String, NotificationMessage> kafkaTemplate;
    private final RateLimiter rateLimiter;

    @Transactional
    public NotificationResponse submit(NotificationRequest request) {
        rateLimiter.checkAndIncrement(request.getRecipientId());

        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity(
            id,
            request.getRecipientId(),
            request.getTemplateCode(),
            request.getChannelType(),
            request.getParameters(),
            request.getPriority()
        );
        repository.save(entity);

        NotificationMessage message = new NotificationMessage(
            id,
            request.getRecipientId(),
            request.getTemplateCode(),
            request.getChannelType(),
            request.getParameters(),
            request.getPriority()
        );
        kafkaTemplate.send(TOPIC, id.toString(), message);

        return new NotificationResponse(id, NotificationStatus.ACCEPTED);
    }
}