package com.ivs.platform.approval;

import com.ivs.platform.security.AuthenticatedUser;
import com.ivs.platform.security.CurrentUserContext;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 站内通知：按权限人群或指定用户可见，阅读状态按用户隔离。 */
@Service
public class NotificationService {
    private final UserNotificationRepository notifications;
    private final UserNotificationReadRepository reads;
    private final CurrentUserContext currentUsers;

    public NotificationService(UserNotificationRepository notifications,
                               UserNotificationReadRepository reads,
                               CurrentUserContext currentUsers) {
        this.notifications = notifications;
        this.reads = reads;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(boolean unreadOnly) {
        AuthenticatedUser user = currentUsers.require();
        Set<UUID> readIds = new HashSet<>();
        reads.findByUserId(user.userId()).forEach(read -> readIds.add(read.getNotificationId()));
        return notifications.findTop200ByOrderByCreatedAtDesc().stream()
                .filter(item -> visible(item, user))
                .map(item -> NotificationView.from(item, readIds.contains(item.getId())))
                .filter(item -> !unreadOnly || !item.read())
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return list(true).size();
    }

    @Transactional
    public NotificationView markRead(UUID id) {
        AuthenticatedUser user = currentUsers.require();
        UserNotification notification = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("站内通知", id));
        if (!visible(notification, user)) throw new NotFoundException("站内通知", id);
        reads.findByNotificationIdAndUserId(id, user.userId())
                .orElseGet(() -> reads.save(UserNotificationRead.create(id, user.userId())));
        return NotificationView.from(notification, true);
    }

    @Transactional
    public int markAllRead() {
        AuthenticatedUser user = currentUsers.require();
        Set<UUID> readIds = new HashSet<>();
        reads.findByUserId(user.userId()).forEach(read -> readIds.add(read.getNotificationId()));
        int count = 0;
        for (UserNotification notification : notifications.findTop200ByOrderByCreatedAtDesc()) {
            if (!visible(notification, user) || readIds.contains(notification.getId())) continue;
            reads.save(UserNotificationRead.create(notification.getId(), user.userId()));
            count++;
        }
        return count;
    }

    @Transactional
    public void notifyPermission(String category, String title, String content, String businessType,
                                 UUID businessId, String permission) {
        notifications.save(UserNotification.create(category, title, content, businessType, businessId, null, permission));
    }

    @Transactional
    public void notifyUser(String category, String title, String content, String businessType,
                           UUID businessId, UUID userId) {
        if (userId == null) return;
        notifications.save(UserNotification.create(category, title, content, businessType, businessId, userId, null));
    }

    private boolean visible(UserNotification notification, AuthenticatedUser user) {
        return user.userId().equals(notification.getAudienceUserId())
                || (notification.getAudiencePermission() != null
                    && user.permissions().contains(notification.getAudiencePermission()));
    }

    public record NotificationView(UUID id, String category, String title, String content,
                                   String businessType, UUID businessId, boolean read, Instant createdAt) {
        static NotificationView from(UserNotification notification, boolean read) {
            return new NotificationView(notification.getId(), notification.getCategory(), notification.getTitle(),
                    notification.getContent(), notification.getBusinessType(), notification.getBusinessId(),
                    read, notification.getCreatedAt());
        }
    }
}
