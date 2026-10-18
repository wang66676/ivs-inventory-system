package com.ivs.platform.setting;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 管理员维护的全局参数。 */
@Entity
@Table(name = "system_setting", schema = "ivs")
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 120)
    private String settingKey;
    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;
    @Column(nullable = false, length = 120)
    private String label;
    @Column(name = "setting_group", nullable = false, length = 80)
    private String settingGroup;
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;
    @Column(length = 300)
    private String description;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected SystemSetting() {}

    public void update(String value, UUID actorId) {
        this.settingValue = value;
        this.updatedBy = actorId;
        this.updatedAt = Instant.now();
    }

    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public String getLabel() { return label; }
    public String getSettingGroup() { return settingGroup; }
    public String getValueType() { return valueType; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
