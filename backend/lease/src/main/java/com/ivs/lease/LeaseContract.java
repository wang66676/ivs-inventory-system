package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 电池租赁合同：一块电池同一时间只能存在一个有效租赁合同。 */
@Entity
@Table(name = "lease_contract", schema = "ivs")
public class LeaseContract {
    @Id
    private UUID id;
    @Column(name = "contract_no", nullable = false, unique = true, length = 40)
    private String contractNo;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "serial_id", nullable = false)
    private UUID serialId;
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "monthly_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyFee;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal deposit;
    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_at_end", nullable = false, length = 20)
    private LeaseOwnershipAtEnd ownershipAtEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaseStatus status;
    @Column(length = 300)
    private String remark;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected LeaseContract() {
    }

    public static LeaseContract create(String contractNo, UUID customerId, UUID serialId, UUID warehouseId,
                                       LocalDate startDate, LocalDate endDate, BigDecimal monthlyFee,
                                       BigDecimal deposit, LeaseOwnershipAtEnd ownershipAtEnd, String remark) {
        LeaseContract value = new LeaseContract();
        if (customerId == null || serialId == null || warehouseId == null) {
            throw new IllegalArgumentException("客户、电池序列号和仓库不能为空");
        }
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("租赁结束日期必须晚于开始日期");
        }
        value.id = UUID.randomUUID();
        value.contractNo = contractNo;
        value.customerId = customerId;
        value.serialId = serialId;
        value.warehouseId = warehouseId;
        value.startDate = startDate;
        value.endDate = endDate;
        value.monthlyFee = money(monthlyFee);
        if (value.monthlyFee.signum() <= 0) throw new IllegalArgumentException("月租必须大于 0");
        value.deposit = money(deposit);
        if (value.deposit.signum() < 0) throw new IllegalArgumentException("押金不能小于 0");
        value.ownershipAtEnd = ownershipAtEnd == null ? LeaseOwnershipAtEnd.RETURN : ownershipAtEnd;
        value.status = LeaseStatus.ACTIVE;
        value.remark = trimToNull(remark);
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void end() {
        if (status != LeaseStatus.ACTIVE) throw new IllegalStateException("只有生效中的租赁合同可以结束");
        status = LeaseStatus.ENDED;
        updatedAt = Instant.now();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getContractNo() { return contractNo; }
    public UUID getCustomerId() { return customerId; }
    public UUID getSerialId() { return serialId; }
    public UUID getWarehouseId() { return warehouseId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public BigDecimal getDeposit() { return deposit; }
    public LeaseOwnershipAtEnd getOwnershipAtEnd() { return ownershipAtEnd; }
    public LeaseStatus getStatus() { return status; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
