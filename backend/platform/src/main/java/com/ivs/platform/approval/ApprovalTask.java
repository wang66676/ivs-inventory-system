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

/** 通用审批待办，业务单据通过 app 层适配创建和完成。 */
@Entity
@Table(name = "approval_task", schema = "ivs")
public class ApprovalTask {
    @Id
    private UUID id;
    @Column(name = "task_no", nullable = false, length = 40)
    private String taskNo;
    @Column(name = "task_type", nullable = false, length = 60)
    private String taskType;
    @Column(name = "business_type", nullable = false, length = 60)
    private String businessType;
    @Column(name = "business_id", nullable = false)
    private UUID businessId;
    @Column(name = "business_no", nullable = false, length = 100)
    private String businessNo;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 1000)
    private String summary;
    @Column(precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "approval_permission", nullable = false, length = 80)
    private String approvalPermission;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalTaskStatus status;
    @Column(name = "applicant_id")
    private UUID applicantId;
    @Column(name = "applicant_name", nullable = false, length = 120)
    private String applicantName;
    @Column(name = "decision_by_id")
    private UUID decisionById;
    @Column(name = "decision_by_name", length = 120)
    private String decisionByName;
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected ApprovalTask() {
    }

    public static ApprovalTask create(String taskNo, String taskType, String businessType, UUID businessId,
                                      String businessNo, String title, String summary, BigDecimal amount,
                                      String approvalPermission, UUID applicantId, String applicantName) {
        ApprovalTask task = new ApprovalTask();
        task.id = UUID.randomUUID();
        task.taskNo = taskNo;
        task.taskType = taskType;
        task.businessType = businessType;
        task.businessId = businessId;
        task.businessNo = businessNo;
        task.title = title;
        task.summary = summary;
        task.amount = amount;
        task.approvalPermission = approvalPermission;
        task.status = ApprovalTaskStatus.PENDING;
        task.applicantId = applicantId;
        task.applicantName = applicantName;
        task.createdAt = Instant.now();
        task.updatedAt = task.createdAt;
        return task;
    }

    public void refresh(String businessNo, String title, String summary, BigDecimal amount,
                        String approvalPermission) {
        if (status != ApprovalTaskStatus.PENDING) return;
        this.businessNo = businessNo;
        this.title = title;
        this.summary = summary;
        this.amount = amount;
        this.approvalPermission = approvalPermission;
        this.updatedAt = Instant.now();
    }

    public void decide(boolean approved, UUID decisionById, String decisionByName, String reason) {
        if (status != ApprovalTaskStatus.PENDING) {
            throw new IllegalStateException("该审批任务已处理: " + status);
        }
        this.status = approved ? ApprovalTaskStatus.APPROVED : ApprovalTaskStatus.REJECTED;
        this.decisionById = decisionById;
        this.decisionByName = decisionByName;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
        this.updatedAt = this.decidedAt;
    }

    public void cancel() {
        if (status != ApprovalTaskStatus.PENDING) return;
        this.status = ApprovalTaskStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTaskNo() { return taskNo; }
    public String getTaskType() { return taskType; }
    public String getBusinessType() { return businessType; }
    public UUID getBusinessId() { return businessId; }
    public String getBusinessNo() { return businessNo; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public BigDecimal getAmount() { return amount; }
    public String getApprovalPermission() { return approvalPermission; }
    public ApprovalTaskStatus getStatus() { return status; }
    public UUID getApplicantId() { return applicantId; }
    public String getApplicantName() { return applicantName; }
    public UUID getDecisionById() { return decisionById; }
    public String getDecisionByName() { return decisionByName; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
