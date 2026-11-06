package com.ivs.platform.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 平台级审批规则：业务模块只通过端口请求判定，不直接依赖本实体。 */
@Entity
@Table(name = "approval_rule", schema = "ivs")
public class ApprovalRule {
    @Id
    @Column(name = "rule_code", length = 80)
    private String ruleCode;
    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;
    @Column(nullable = false, length = 40)
    private String scenario;
    @Column(nullable = false)
    private boolean enabled;
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 30)
    private ApprovalMetricType metricType;
    @Column(name = "threshold_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdValue;
    @Column(name = "approval_permission", nullable = false, length = 80)
    private String approvalPermission;
    @Column(length = 500)
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

    protected ApprovalRule() {
    }

    public void update(boolean enabled, ApprovalMetricType metricType, BigDecimal thresholdValue,
                       String approvalPermission, String description, UUID actorId) {
        this.enabled = enabled;
        this.metricType = metricType;
        this.thresholdValue = thresholdValue;
        this.approvalPermission = approvalPermission;
        this.description = description;
        this.updatedBy = actorId;
        this.updatedAt = Instant.now();
    }

    public String getRuleCode() { return ruleCode; }
    public String getRuleName() { return ruleName; }
    public String getScenario() { return scenario; }
    public boolean isEnabled() { return enabled; }
    public ApprovalMetricType getMetricType() { return metricType; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public String getApprovalPermission() { return approvalPermission; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
