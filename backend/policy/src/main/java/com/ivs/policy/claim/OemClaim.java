package com.ivs.policy.claim;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 厂家申报单：按厂家 + 政策 + 周期单张生成，或同厂家同周期合并生成。 */
@Entity
@Table(name = "oem_claim", schema = "ivs")
public class OemClaim {

    @Id private UUID id;
    @Column(name = "claim_no", nullable = false, unique = true, length = 40) private String claimNo;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(name = "policy_id") private UUID policyId;
    @Column(name = "period_code", nullable = false, length = 20) private String periodCode;
    @Column(name = "settle_cycle", nullable = false, length = 20) private String settleCycle;
    @Column(name = "merged_claim", nullable = false) private boolean mergedClaim;
    @Column(name = "claim_amount", nullable = false, precision = 18, scale = 2) private BigDecimal claimAmount;
    @Column(name = "confirmed_amount", nullable = false, precision = 18, scale = 2) private BigDecimal confirmedAmount;
    @Column(name = "difference_amount", nullable = false, precision = 18, scale = 2) private BigDecimal differenceAmount;
    @Column(name = "cutoff_at") private Instant cutoffAt;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "settled_at") private Instant settledAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(name = "invoice_no", length = 100) private String invoiceNo;
    @Column(length = 300) private String remark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected OemClaim() {
    }

    public static OemClaim create(String claimNo, UUID supplierId, UUID policyId, String periodCode,
                                  String settleCycle, boolean mergedClaim, Instant cutoffAt, String remark) {
        OemClaim claim = new OemClaim();
        claim.id = UUID.randomUUID();
        claim.claimNo = claimNo;
        claim.supplierId = supplierId;
        claim.policyId = policyId;
        claim.periodCode = periodCode;
        claim.settleCycle = settleCycle == null ? "MONTHLY" : settleCycle;
        claim.mergedClaim = mergedClaim;
        claim.claimAmount = money(BigDecimal.ZERO);
        claim.confirmedAmount = money(BigDecimal.ZERO);
        claim.differenceAmount = money(BigDecimal.ZERO);
        claim.cutoffAt = cutoffAt;
        claim.status = "DRAFT";
        claim.remark = remark;
        claim.createdAt = Instant.now();
        claim.updatedAt = claim.createdAt;
        return claim;
    }

    public void addAmount(BigDecimal amount) {
        requireDraft();
        this.claimAmount = money(this.claimAmount.add(amount));
    }

    public void removeAmount(BigDecimal amount) {
        requireDraft();
        this.claimAmount = money(this.claimAmount.subtract(amount));
        if (this.claimAmount.signum() < 0) this.claimAmount = money(BigDecimal.ZERO);
    }

    public void submit() {
        requireDraft();
        if (claimAmount.signum() <= 0) throw new IllegalStateException("申报金额必须大于 0");
        this.status = "SUBMITTED";
        this.submittedAt = Instant.now();
    }

    public void confirm(BigDecimal confirmed, boolean disputed) {
        if (!"SUBMITTED".equals(status) && !"PARTIALLY_CONFIRMED".equals(status) && !"DISPUTED".equals(status)) {
            throw new IllegalStateException("只有已提交或待复确认的申报单可以登记厂家确认");
        }
        BigDecimal value = money(confirmed);
        if (value.signum() < 0 || value.compareTo(claimAmount) > 0) {
            throw new IllegalArgumentException("厂家确认额不能为负且不能大于申报额");
        }
        this.confirmedAmount = value;
        this.differenceAmount = money(claimAmount.subtract(value));
        this.status = disputed ? "DISPUTED" : (value.compareTo(claimAmount) == 0 ? "CONFIRMED" : "PARTIALLY_CONFIRMED");
        this.confirmedAt = Instant.now();
    }

    public void settle(UUID invoiceId, String invoiceNo) {
        if (!"CONFIRMED".equals(status) && !"PARTIALLY_CONFIRMED".equals(status)) {
            throw new IllegalStateException("只有厂家已确认的申报单可以登记红字结算");
        }
        this.status = "SETTLED";
        this.invoiceId = invoiceId;
        this.invoiceNo = invoiceNo;
        this.settledAt = Instant.now();
    }

    public void close() {
        if (!"SETTLED".equals(status) && !"CONFIRMED".equals(status) && !"PARTIALLY_CONFIRMED".equals(status)) {
            throw new IllegalStateException("申报单尚未结算，不能关闭");
        }
        this.status = "CLOSED";
        this.closedAt = Instant.now();
    }

    private void requireDraft() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("只有草稿申报单可以编辑");
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (claimAmount == null) claimAmount = money(BigDecimal.ZERO);
        if (confirmedAmount == null) confirmedAmount = money(BigDecimal.ZERO);
        if (differenceAmount == null) differenceAmount = money(BigDecimal.ZERO);
        if (status == null) status = "DRAFT";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getClaimNo() { return claimNo; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPolicyId() { return policyId; }
    public String getPeriodCode() { return periodCode; }
    public String getSettleCycle() { return settleCycle; }
    public boolean isMergedClaim() { return mergedClaim; }
    public BigDecimal getClaimAmount() { return claimAmount; }
    public BigDecimal getConfirmedAmount() { return confirmedAmount; }
    public BigDecimal getDifferenceAmount() { return differenceAmount; }
    public Instant getCutoffAt() { return cutoffAt; }
    public String getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
