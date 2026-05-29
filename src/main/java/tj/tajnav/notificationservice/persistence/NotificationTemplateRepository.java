package tj.tajnav.notificationservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tj.tajnav.notificationservice.domain.ChannelType;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long> {
    Optional<NotificationTemplateEntity> findByTemplateCodeAndChannelType(String templateCode, ChannelType channelType);
}