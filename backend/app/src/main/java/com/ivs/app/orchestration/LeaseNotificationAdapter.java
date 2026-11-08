package com.ivs.app.orchestration;

import com.ivs.lease.LeaseNotificationPort;
import com.ivs.platform.approval.NotificationService;
import com.ivs.platform.security.PermissionCodes;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** 把租赁提醒适配为站内通知，同时投递给租赁管理和财务只读角色。 */
@Component
public class LeaseNotificationAdapter implements LeaseNotificationPort {
    private final NotificationService notifications;

    public LeaseNotificationAdapter(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void notifyLeaseReminder(String title, String content, UUID contractId) {
        notifications.notifyPermissions("LEASE_RENT", title, content, "LEASE_CONTRACT", contractId,
                Set.of(PermissionCodes.LEASE_MANAGE, PermissionCodes.FINANCE_READ));
    }
}
