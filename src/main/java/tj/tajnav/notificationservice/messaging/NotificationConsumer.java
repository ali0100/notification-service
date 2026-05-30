package tj.tajnav.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tj.tajnav.notificationservice.domain.NotificationStatus;
import tj.tajnav.notificationservice.persistence.NotificationEntity;
import tj.tajnav.notificationservice.persistence.NotificationRepository;
import tj.tajnav.notificationservice.sender.SenderRegistry;
import tj.tajnav.notificationservice.template.TemplateRenderer;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationRepository repository;
    private final TemplateRenderer templateRenderer;
    private final SenderRegistry senderRegistry;

    @KafkaListener(topics = "notification.requests", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(NotificationMessage message) {
        NotificationEntity entity = repository.findById(message.getNotificationId())
            .orElseThrow(() -> new IllegalStateException("Notification not found: " + message.getNotificationId()));

        if (!entity.isStatusAccepted()) {
            return;
        }
        entity.transitionTo(NotificationStatus.PROCESSING);
        repository.save(entity);

        try {
            String rendered = templateRenderer.render(
                message.getTemplateCode(),
                message.getChannelType(),
                message.getParameters()
            );
            senderRegistry.get(message.getChannelType())
                .send(message.getRecipientId().toString(), rendered);

            entity.transitionTo(NotificationStatus.SENT);
        } catch (Exception e) {
            log.error("Failed to process notification {}: {}", message.getNotificationId(), e.getMessage());
            entity.transitionTo(NotificationStatus.FAILED);
        }

        repository.save(entity);
    }
}