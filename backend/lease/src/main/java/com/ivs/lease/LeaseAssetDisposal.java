package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 电池退役处置记录。 */
@Entity
@Table(name = "lease_asset_disposal", schema = "ivs")
public class LeaseAssetDisposal {
    @Id
    private UUID id;
    @Column(name = "disposal_no", nullable = false, unique = true, length = 40)
    private String disposalNo;
    @Column(name = "serial_id", nullable = false)
    private UUID serialId;
    @Column(name = "contract_id")
    private UUID contractId;
    @Enumerated(EnumType.STRING)
    @Column(name = "disposition_type", nullable = false, length = 20)
    private LeaseDispositionType dispositionType;
    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;
    @Column(name = "soh_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal sohPercent;
    @Column(length = 120)
    private String destination;
    @Column(nullable = false, length = 300)
    private String reason;
    @Column(length = 300)
    private String remark;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseAssetDisposal() {
    }

    public static LeaseAssetDisposal create(String disposalNo, UUID serialId, UUID contractId,
                                            LeaseDispositionType dispositionType, LocalDate disposalDate,
                                            BigDecimal sohPercent, String destination, String reason, String remark) {
        LeaseAssetDisposal disposal = new LeaseAssetDisposal();
        disposal.id = UUID.randomUUID();
        disposal.disposalNo = disposalNo;
        disposal.serialId = serialId;
        disposal.contractId = contractId;
        disposal.dispositionType = dispositionType;
        disposal.disposalDate = disposalDate == null ? LocalDate.now() : disposalDate;
        disposal.sohPercent = (sohPercent == null ? BigDecimal.ZERO : sohPercent).setScale(2, RoundingMode.HALF_UP);
        if (disposal.sohPercent.signum() < 0 || disposal.sohPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("处置时 SOH 必须在 0 到 100 之间");
        }
        disposal.destination = trimToNull(destination);
        disposal.reason = requireText(reason, "请填写退役处置原因");
        disposal.remark = trimToNull(remark);
        disposal.createdAt = Instant.now();
        return disposal;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getDisposalNo() { return disposalNo; }
    public UUID getSerialId() { return serialId; }
    public UUID getContractId() { return contractId; }
    public LeaseDispositionType getDispositionType() { return dispositionType; }
    public LocalDate getDisposalDate() { return disposalDate; }
    public BigDecimal getSohPercent() { return sohPercent; }
    public String getDestination() { return destination; }
    public String getReason() { return reason; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}
