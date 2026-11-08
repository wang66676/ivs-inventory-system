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

/** 租赁电池健康档案：SOH、循环次数、累计里程和检测结论。 */
@Entity
@Table(name = "lease_battery_health", schema = "ivs")
public class LeaseBatteryHealth {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "serial_id", nullable = false)
    private UUID serialId;
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;
    @Column(name = "soh_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal sohPercent;
    @Column(name = "cycle_count", nullable = false)
    private int cycleCount;
    @Column(name = "mileage_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal mileageKm;
    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_result", nullable = false, length = 20)
    private LeaseInspectionResult inspectionResult;
    @Column(length = 80)
    private String inspector;
    @Column(length = 300)
    private String remark;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseBatteryHealth() {
    }

    public static LeaseBatteryHealth create(UUID contractId, UUID serialId, LocalDate recordDate,
                                            BigDecimal sohPercent, Integer cycleCount, BigDecimal mileageKm,
                                            LeaseInspectionResult inspectionResult, String inspector, String remark) {
        LeaseBatteryHealth record = new LeaseBatteryHealth();
        record.id = UUID.randomUUID();
        record.contractId = contractId;
        record.serialId = serialId;
        record.recordDate = recordDate == null ? LocalDate.now() : recordDate;
        record.sohPercent = (sohPercent == null ? BigDecimal.ZERO : sohPercent).setScale(2, RoundingMode.HALF_UP);
        if (record.sohPercent.signum() < 0 || record.sohPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("SOH 必须在 0 到 100 之间");
        }
        record.cycleCount = cycleCount == null ? 0 : cycleCount;
        if (record.cycleCount < 0) throw new IllegalArgumentException("循环次数不能小于 0");
        record.mileageKm = (mileageKm == null ? BigDecimal.ZERO : mileageKm).setScale(2, RoundingMode.HALF_UP);
        if (record.mileageKm.signum() < 0) throw new IllegalArgumentException("累计里程不能小于 0");
        record.inspectionResult = inspectionResult == null ? LeaseInspectionResult.NORMAL : inspectionResult;
        record.inspector = trimToNull(inspector);
        record.remark = trimToNull(remark);
        record.createdAt = Instant.now();
        return record;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public UUID getSerialId() { return serialId; }
    public LocalDate getRecordDate() { return recordDate; }
    public BigDecimal getSohPercent() { return sohPercent; }
    public int getCycleCount() { return cycleCount; }
    public BigDecimal getMileageKm() { return mileageKm; }
    public LeaseInspectionResult getInspectionResult() { return inspectionResult; }
    public String getInspector() { return inspector; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}
