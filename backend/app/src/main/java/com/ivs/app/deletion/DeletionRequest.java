package com.ivs.app.deletion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 非管理员删除业务数据前提交给管理员的审批单。 */
@Entity
@Table(name = "data_delete_request", schema = "ivs")
public class DeletionRequest {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 40)
    private DeletionBusinessType businessType;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "business_no", nullable = false, length = 100)
    private String businessNo;

    @Column(name = "module_name", nullable = false, length = 80)
    private String moduleName;

    @Column(length = 500)
    private String reason;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "requester_name", nullable = false, length = 120)
    private String requesterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeletionRequestStatus status;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewer_name", length = 120)
    private String reviewerName;

    @Column(name = "review_remark", length = 500)
    private String reviewRemark;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected DeletionRequest() {
    }

    public static DeletionRequest create(DeletionBusinessType type, UUID businessId, String businessNo,
                                         String reason, UUID requesterId, String requesterName) {
        Instant now = Instant.now();
        DeletionRequest value = new DeletionRequest();
        value.id = UUID.randomUUID();
        value.businessType = type;
        value.businessId = businessId;
        value.businessNo = businessNo;
        value.moduleName = type.label();
        value.reason = reason == null || reason.isBlank() ? null : reason.trim();
        value.requesterId = requesterId;
        value.requesterName = requesterName;
        value.status = DeletionRequestStatus.PENDING;
        value.requestedAt = now;
        value.createdAt = now;
        value.updatedAt = now;
        return value;
    }

    public void approve(UUID reviewerId, String reviewerName, String remark) {
        ensurePending();
        this.status = DeletionRequestStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewRemark = normalize(remark);
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewerId, String reviewerName, String remark) {
        ensurePending();
        if (remark == null || remark.isBlank()) {
            throw new IllegalStateException("驳回时必须填写原因");
        }
        this.status = DeletionRequestStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewRemark = remark.trim();
        this.reviewedAt = Instant.now();
    }

    private void ensurePending() {
        if (status != DeletionRequestStatus.PENDING) {
            throw new IllegalStateException("该删除申请已经处理，不能重复操作");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public DeletionBusinessType getBusinessType() { return businessType; }
    public UUID getBusinessId() { return businessId; }
    public String getBusinessNo() { return businessNo; }
    public String getModuleName() { return moduleName; }
    public String getReason() { return reason; }
    public UUID getRequesterId() { return requesterId; }
    public String getRequesterName() { return requesterName; }
    public DeletionRequestStatus getStatus() { return status; }
    public UUID getReviewerId() { return reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public String getReviewRemark() { return reviewRemark; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
}
