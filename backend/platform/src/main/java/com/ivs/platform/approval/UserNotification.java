package com.ivs.platform.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 站内通知：可按权限人群或指定用户投递。 */
@Entity
@Table(name = "user_notification", schema = "ivs")
public class UserNotification {
    @Id
    private UUID id;
    @Column(nullable = false, length = 60)
    private String category;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(name = "business_type", length = 60)
    private String businessType;
    @Column(name = "business_id")
    private UUID businessId;
    @Column(name = "audience_user_id")
    private UUID audienceUserId;
    @Column(name = "audience_permission", length = 80)
    private String audiencePermission;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserNotification() {
    }

    public static UserNotification create(String category, String title, String content,
                                          String businessType, UUID businessId,
                                          UUID audienceUserId, String audiencePermission) {
        UserNotification notification = new UserNotification();
        notification.id = UUID.randomUUID();
        notification.category = category;
        notification.title = title;
        notification.content = content;
        notification.businessType = businessType;
        notification.businessId = businessId;
        notification.audienceUserId = audienceUserId;
        notification.audiencePermission = audiencePermission;
        notification.createdAt = Instant.now();
        return notification;
    }

    public UUID getId() { return id; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getBusinessType() { return businessType; }
    public UUID getBusinessId() { return businessId; }
    public UUID getAudienceUserId() { return audienceUserId; }
    public String getAudiencePermission() { return audiencePermission; }
    public Instant getCreatedAt() { return createdAt; }
}
