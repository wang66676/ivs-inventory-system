package com.ivs.lease;

import java.util.UUID;

/** 租赁月租提醒端口，平台通知实现位于 app 层。 */
public interface LeaseNotificationPort {
    void notifyLeaseReminder(String title, String content, UUID contractId);
}
