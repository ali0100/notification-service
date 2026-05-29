package tj.tajnav.notificationservice.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tj.tajnav.notificationservice.domain.ChannelType;

@Entity
@Table(name = "notification_templates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"template_code", "channel_type"}))
@Getter
@NoArgsConstructor
public class NotificationTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;
}