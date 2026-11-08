package com.ivs.lease.api;

import com.ivs.lease.LeaseContract;
import com.ivs.lease.LeaseContractChange;
import com.ivs.lease.LeaseBatteryHealth;
import com.ivs.lease.LeaseAssetDisposal;
import com.ivs.lease.LeaseDepositTransaction;
import com.ivs.lease.LeaseMasterDataPort;
import com.ivs.lease.LeaseOwnershipAtEnd;
import com.ivs.lease.LeaseReceivablePort;
import com.ivs.lease.LeaseSchedule;
import com.ivs.lease.LeaseSettlement;
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

    public record DueGenerationResult(LocalDate throughDate, int activeContracts, int generatedReceivables) {
    }

    public record AutomationResult(LocalDate asOfDate, int reminderDays, int activeContracts,
                                   int generatedReceivables, int dueSoonReminders, int overdueReminders) {
    }

    public record DepositCommand(BigDecimal amount, LocalDate transactionDate, String method, String remark) {
    }

    public record EndLeaseCommand(LocalDate settlementDate, boolean applyDeposit, String reason) {
    }

    public record RenewLeaseCommand(LocalDate newEndDate, BigDecimal newMonthlyFee, String reason) {
    }

    public record ContractChangeView(UUID id, String type, LocalDate oldEndDate, LocalDate newEndDate,
                                     BigDecimal oldMonthlyFee, BigDecimal newMonthlyFee, String reason,
                                     Instant createdAt) {
        public static ContractChangeView from(LeaseContractChange change) {
            return new ContractChangeView(change.getId(), change.getChangeType(), change.getOldEndDate(),
                    change.getNewEndDate(), change.getOldMonthlyFee(), change.getNewMonthlyFee(),
                    change.getReason(), change.getCreatedAt());
        }
    }

    public record RetireAssetCommand(BigDecimal sohPercent, String dispositionType, LocalDate disposalDate,
                                     String destination, String reason, String remark) {
    }

    public record AssetDisposalView(UUID id, String disposalNo, UUID serialId, UUID contractId,
                                    String dispositionType, LocalDate disposalDate, BigDecimal sohPercent,
                                    String destination, String reason, String remark, Instant createdAt) {
        public static AssetDisposalView from(LeaseAssetDisposal disposal) {
            return new AssetDisposalView(disposal.getId(), disposal.getDisposalNo(), disposal.getSerialId(),
                    disposal.getContractId(), disposal.getDispositionType().name(), disposal.getDisposalDate(),
                    disposal.getSohPercent(), disposal.getDestination(), disposal.getReason(),
                    disposal.getRemark(), disposal.getCreatedAt());
        }
    }

    public record HealthRecordCommand(LocalDate recordDate, BigDecimal sohPercent, Integer cycleCount,
                                      BigDecimal mileageKm, String inspectionResult, String inspector, String remark) {
    }

    public record HealthRecordView(UUID id, LocalDate recordDate, BigDecimal sohPercent, int cycleCount,
                                   BigDecimal mileageKm, String inspectionResult, String inspector,
                                   String remark, Instant createdAt) {
        public static HealthRecordView from(LeaseBatteryHealth record) {
            return new HealthRecordView(record.getId(), record.getRecordDate(), record.getSohPercent(),
                    record.getCycleCount(), record.getMileageKm(), record.getInspectionResult().name(),
                    record.getInspector(), record.getRemark(), record.getCreatedAt());
        }
    }

    public record LeaseAssetReportSummary(int totalContracts, int activeContracts, int attentionAssets,
                                          BigDecimal activeMonthlyRent, BigDecimal generatedRent,
                                          BigDecimal receivedRent, BigDecimal outstandingRent,
                                          BigDecimal depositAvailable, BigDecimal purchaseCost,
                                          BigDecimal accumulatedDepreciation, BigDecimal netValue) {
    }

    public record LeaseAssetReportRow(UUID contractId, String contractNo, String customerName, String serialNo,
                                      String productName, String serialStatus, LeaseStatus status, LocalDate startDate,
                                      LocalDate endDate, BigDecimal monthlyFee, LocalDate settlementDate,
                                      BigDecimal purchaseCost, BigDecimal monthlyDepreciation,
                                      BigDecimal accumulatedDepreciation, BigDecimal netValue, int depreciationMonths,
                                      BigDecimal generatedRent, BigDecimal receivedRent, BigDecimal outstandingRent,
                                      BigDecimal depositExpected, BigDecimal depositReceived,
                                      BigDecimal depositApplied, BigDecimal depositRefunded,
                                      BigDecimal depositAvailable, BigDecimal latestSohPercent,
                                      Integer latestCycleCount, BigDecimal latestMileageKm,
                                      String latestInspectionResult, LocalDate latestInspectionDate) {
    }

    public record LeaseAssetReportView(Instant generatedAt, LeaseAssetReportSummary summary,
                                       List<LeaseAssetReportRow> rows) {
    }

    public record DepositSummary(BigDecimal expected, BigDecimal received, BigDecimal applied,
                                 BigDecimal refunded, BigDecimal available) {
    }

    public record DepositTransactionView(UUID id, String type, BigDecimal amount, LocalDate transactionDate,
                                         String method, String referenceNo, String remark, Instant createdAt) {
        public static DepositTransactionView from(LeaseDepositTransaction transaction) {
            return new DepositTransactionView(transaction.getId(), transaction.getTxnType().name(),
                    transaction.getAmount(), transaction.getTxnDate(), transaction.getMethod(),
                    transaction.getReferenceNo(), transaction.getRemark(), transaction.getCreatedAt());
        }
    }

    public record SettlementView(UUID id, LocalDate settlementDate, BigDecimal outstandingBefore,
                                 BigDecimal depositApplied, BigDecimal outstandingAfter,
                                 BigDecimal depositRefundable, String reason, Instant createdAt) {
        public static SettlementView from(LeaseSettlement settlement) {
            if (settlement == null) return null;
            return new SettlementView(settlement.getId(), settlement.getSettlementDate(),
                    settlement.getOutstandingBefore(), settlement.getDepositApplied(),
                    settlement.getOutstandingAfter(), settlement.getDepositRefundable(),
                    settlement.getReason(), settlement.getCreatedAt());
        }
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
                                    UUID serialId, String serialNo, String productName, String serialStatus,
                                    UUID warehouseId,
                                    LocalDate startDate, LocalDate endDate, BigDecimal monthlyFee,
                                    BigDecimal purchaseCost, BigDecimal monthlyDepreciation,
                                    BigDecimal accumulatedDepreciation, BigDecimal netValue, int depreciationMonths,
                                    BigDecimal deposit,
                                    BigDecimal depositReceived, BigDecimal depositApplied,
                                    BigDecimal depositRefunded, BigDecimal depositAvailable,
                                    LeaseOwnershipAtEnd ownershipAtEnd, LeaseStatus status, String remark,
                                    Instant createdAt, List<LeaseScheduleView> schedules,
                                    List<DepositTransactionView> depositTransactions, SettlementView settlement,
                                    List<ContractChangeView> changes) {
        public static LeaseContractView from(LeaseContract contract, String customerName,
                                             LeaseMasterDataPort.SerialInfo serial,
                                             List<LeaseScheduleView> schedules, DepositSummary depositSummary,
                                             List<DepositTransactionView> depositTransactions,
                                             SettlementView settlement,
                                             com.ivs.lease.LeaseDepreciationService.Summary depreciation,
                                             List<ContractChangeView> changes) {
            return new LeaseContractView(contract.getId(), contract.getContractNo(), contract.getCustomerId(),
                    customerName, contract.getSerialId(), serial.serialNo(), serial.productName(), serial.status(),
                    contract.getWarehouseId(), contract.getStartDate(), contract.getEndDate(),
                    contract.getMonthlyFee(), depreciation.purchaseCost(), depreciation.monthlyDepreciation(),
                    depreciation.accumulatedDepreciation(), depreciation.netValue(), depreciation.depreciationMonths(),
                    contract.getDeposit(), depositSummary.received(), depositSummary.applied(),
                    depositSummary.refunded(), depositSummary.available(),
                    contract.getOwnershipAtEnd(), contract.getStatus(), contract.getRemark(), contract.getCreatedAt(),
                    schedules, depositTransactions, settlement, changes);
        }
    }
}
