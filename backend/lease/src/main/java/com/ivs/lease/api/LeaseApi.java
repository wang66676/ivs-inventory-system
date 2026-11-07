package com.ivs.lease.api;

import com.ivs.lease.LeaseContract;
import com.ivs.lease.LeaseMasterDataPort;
import com.ivs.lease.LeaseOwnershipAtEnd;
import com.ivs.lease.LeaseReceivablePort;
import com.ivs.lease.LeaseSchedule;
import com.ivs.lease.LeaseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LeaseApi {
    private LeaseApi() {
    }

    public record CreateLeaseCommand(UUID customerId, UUID serialId, LocalDate startDate, LocalDate endDate,
                                     BigDecimal monthlyFee, BigDecimal deposit,
                                     LeaseOwnershipAtEnd ownershipAtEnd, String remark) {
    }

    public record LeaseScheduleView(UUID id, int periodNo, LocalDate dueDate, BigDecimal amount,
                                    UUID receivableId, String receivableNo, String receivableStatus,
                                    BigDecimal receivedAmount, BigDecimal outstanding) {
        public static LeaseScheduleView from(LeaseSchedule schedule, LeaseReceivablePort.ReceivableInfo receivable) {
            return new LeaseScheduleView(schedule.getId(), schedule.getPeriodNo(), schedule.getDueDate(),
                    schedule.getAmount(), schedule.getReceivableId(),
                    receivable == null ? null : receivable.receivableNo(),
                    receivable == null ? "NOT_GENERATED" : receivable.status(),
                    receivable == null ? BigDecimal.ZERO : receivable.receivedAmount(),
                    receivable == null ? schedule.getAmount() : receivable.outstanding());
        }
    }

    public record LeaseContractView(UUID id, String contractNo, UUID customerId, String customerName,
                                    UUID serialId, String serialNo, String productName, UUID warehouseId,
                                    LocalDate startDate, LocalDate endDate, BigDecimal monthlyFee, BigDecimal deposit,
                                    LeaseOwnershipAtEnd ownershipAtEnd, LeaseStatus status, String remark,
                                    Instant createdAt, List<LeaseScheduleView> schedules) {
        public static LeaseContractView from(LeaseContract contract, String customerName,
                                             LeaseMasterDataPort.SerialInfo serial,
                                             List<LeaseScheduleView> schedules) {
            return new LeaseContractView(contract.getId(), contract.getContractNo(), contract.getCustomerId(),
                    customerName, contract.getSerialId(), serial.serialNo(), serial.productName(),
                    contract.getWarehouseId(), contract.getStartDate(), contract.getEndDate(),
                    contract.getMonthlyFee(), contract.getDeposit(), contract.getOwnershipAtEnd(), contract.getStatus(),
                    contract.getRemark(), contract.getCreatedAt(), schedules);
        }
    }
}
