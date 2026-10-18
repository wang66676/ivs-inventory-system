package com.ivs.platform.security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_user", schema = "ivs")
public class PlatformUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String username;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "company_id") private UUID companyId;
    @Column(name = "store_id") private UUID storeId;
    @Column(name = "team_id") private UUID teamId;
    @Enumerated(EnumType.STRING) @Column(name = "data_scope", nullable = false, length = 20) private DataScope dataScope;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UserStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PlatformUser() {}

    public static PlatformUser create(String username, String displayName, String passwordHash, DataScope dataScope,
                                      UUID companyId, UUID storeId, UUID teamId) {
        PlatformUser user = new PlatformUser();
        user.id = UUID.randomUUID();
        user.username = username;
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.dataScope = dataScope;
        user.companyId = companyId;
        user.storeId = storeId;
        user.teamId = teamId;
        user.status = UserStatus.ACTIVE;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    /** 更新密码摘要（调用方负责加密与强度校验）。 */
    public void changePassword(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("密码摘要不能为空");
        }
        this.passwordHash = passwordHash;
    }

    public void disable() { this.status = UserStatus.DISABLED; }
    public void enable() { this.status = UserStatus.ACTIVE; }

    @PrePersist void prePersist() { Instant now = Instant.now(); if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public UUID getCompanyId() { return companyId; }
    public UUID getStoreId() { return storeId; }
    public UUID getTeamId() { return teamId; }
    public DataScope getDataScope() { return dataScope; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
