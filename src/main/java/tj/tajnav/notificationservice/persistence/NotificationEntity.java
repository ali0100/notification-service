package tj.tajnav.notificationservice.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tj.tajnav.notificationservice.domain.ChannelType;
import tj.tajnav.notificationservice.domain.InvalidStatusTransitionException;
import tj.tajnav.notificationservice.domain.NotificationStatus;
import tj.tajnav.notificationservice.domain.Priority;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationEntity(UUID id, UUID recipientId, String templateCode,
                               ChannelType channelType, Map<String, Object> parameters,
                               Priority priority) {
        this.id = id;
        this.recipientId = recipientId;
        this.templateCode = templateCode;
        this.channelType = channelType;
        this.parameters = parameters;
        this.priority = priority;
        this.status = NotificationStatus.ACCEPTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(NotificationStatus next) {
        status.validateTransitionTo(next);
        this.status = next;
        this.updatedAt = Instant.now();
    }
}