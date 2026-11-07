package com.ivs.platform.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification_read", schema = "ivs")
public class UserNotificationRead {
    @Id
    private UUID id;
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected UserNotificationRead() {
    }

    public static UserNotificationRead create(UUID notificationId, UUID userId) {
        UserNotificationRead read = new UserNotificationRead();
        read.id = UUID.randomUUID();
        read.notificationId = notificationId;
        read.userId = userId;
        read.readAt = Instant.now();
        return read;
    }

    public UUID getNotificationId() { return notificationId; }
    public UUID getUserId() { return userId; }
    public Instant getReadAt() { return readAt; }
}
