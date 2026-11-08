package com.ivs.lease;

import com.ivs.lease.api.LeaseApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 租赁资产经营报表：租金、押金和最新健康状态。 */
@Service
public class LeaseReportService {
    private final LeaseContractRepository contractRepository;
    private final LeaseScheduleRepository scheduleRepository;
    private final LeaseDepositTransactionRepository depositRepository;
    private final LeaseSettlementRepository settlementRepository;
    private final LeaseBatteryHealthRepository healthRepository;
    private final LeaseDepreciationService depreciationService;
    private final LeaseMasterDataPort masterData;
    private final LeaseReceivablePort receivables;

    public LeaseReportService(LeaseContractRepository contractRepository,
                              LeaseScheduleRepository scheduleRepository,
                              LeaseDepositTransactionRepository depositRepository,
                              LeaseSettlementRepository settlementRepository,
                              LeaseBatteryHealthRepository healthRepository,
                              LeaseDepreciationService depreciationService,
                              LeaseMasterDataPort masterData,
                              LeaseReceivablePort receivables) {
        this.contractRepository = contractRepository;
        this.scheduleRepository = scheduleRepository;
        this.depositRepository = depositRepository;
        this.settlementRepository = settlementRepository;
        this.healthRepository = healthRepository;
        this.depreciationService = depreciationService;
        this.masterData = masterData;
        this.receivables = receivables;
    }

    @Transactional
    public LeaseApi.LeaseAssetReportView report() {
        depreciationService.ensureAll();
        List<LeaseContract> contracts = contractRepository.findAllByOrderByCreatedAtDesc();
        List<LeaseApi.LeaseAssetReportRow> rows = new ArrayList<>();
        int active = 0;
        int attention = 0;
        BigDecimal activeMonthlyRent = BigDecimal.ZERO;
        BigDecimal generatedRent = BigDecimal.ZERO;
        BigDecimal receivedRent = BigDecimal.ZERO;
        BigDecimal outstandingRent = BigDecimal.ZERO;
        BigDecimal depositAvailable = BigDecimal.ZERO;
        BigDecimal totalPurchaseCost = BigDecimal.ZERO;
        BigDecimal totalAccumulatedDepreciation = BigDecimal.ZERO;
        BigDecimal totalNetValue = BigDecimal.ZERO;

        for (LeaseContract contract : contracts) {
            LeaseMasterDataPort.CustomerInfo customer = masterData.requireCustomer(contract.getCustomerId());
            LeaseMasterDataPort.SerialInfo serial = masterData.requireSerial(contract.getSerialId());
            BigDecimal generated = BigDecimal.ZERO;
            BigDecimal received = BigDecimal.ZERO;
            BigDecimal outstanding = BigDecimal.ZERO;
            for (LeaseSchedule schedule : scheduleRepository.findByContractIdOrderByPeriodNoAsc(contract.getId())) {
                if (schedule.getReceivableId() == null) continue;
                LeaseReceivablePort.ReceivableInfo receivable = receivables.get(schedule.getReceivableId());
                generated = generated.add(receivable.amount());
                received = received.add(receivable.receivedAmount());
                outstanding = outstanding.add(receivable.outstanding());
            }
            List<LeaseDepositTransaction> deposits = depositRepository
                    .findByContractIdOrderByTxnDateAscCreatedAtAsc(contract.getId());
            BigDecimal depositReceived = BigDecimal.ZERO;
            BigDecimal depositApplied = BigDecimal.ZERO;
            BigDecimal depositRefunded = BigDecimal.ZERO;
            for (LeaseDepositTransaction transaction : deposits) {
                switch (transaction.getTxnType()) {
                    case COLLECT -> depositReceived = depositReceived.add(transaction.getAmount());
                    case APPLY_RENT -> depositApplied = depositApplied.add(transaction.getAmount());
                    case REFUND -> depositRefunded = depositRefunded.add(transaction.getAmount());
                }
            }
            BigDecimal available = depositReceived.subtract(depositApplied).subtract(depositRefunded);
            LeaseBatteryHealth latest = healthRepository
                    .findFirstByContractIdOrderByRecordDateDescCreatedAtDesc(contract.getId()).orElse(null);
            LeaseDepreciationService.Summary depreciation = depreciationService.summary(contract);
            boolean needsAttention = latest != null && latest.getInspectionResult() != LeaseInspectionResult.NORMAL;
            if (needsAttention) attention++;
            if (contract.getStatus() == LeaseStatus.ACTIVE) {
                active++;
                activeMonthlyRent = activeMonthlyRent.add(contract.getMonthlyFee());
            }
            generatedRent = generatedRent.add(generated);
            receivedRent = receivedRent.add(received);
            outstandingRent = outstandingRent.add(outstanding);
            depositAvailable = depositAvailable.add(available);
            totalPurchaseCost = totalPurchaseCost.add(depreciation.purchaseCost());
            totalAccumulatedDepreciation = totalAccumulatedDepreciation.add(depreciation.accumulatedDepreciation());
            totalNetValue = totalNetValue.add(depreciation.netValue());
            rows.add(new LeaseApi.LeaseAssetReportRow(contract.getId(), contract.getContractNo(), customer.name(),
                    serial.serialNo(), serial.productName(), contract.getStatus(), contract.getStartDate(),
                    contract.getEndDate(), contract.getMonthlyFee(),
                    settlementRepository.findByContractId(contract.getId())
                            .map(LeaseSettlement::getSettlementDate).orElse(null),
                    depreciation.purchaseCost(), depreciation.monthlyDepreciation(),
                    depreciation.accumulatedDepreciation(), depreciation.netValue(), depreciation.depreciationMonths(),
                    money(generated), money(received), money(outstanding), contract.getDeposit(),
                    money(depositReceived), money(depositApplied), money(depositRefunded), money(available),
                    latest == null ? null : latest.getSohPercent(),
                    latest == null ? null : latest.getCycleCount(),
                    latest == null ? null : latest.getMileageKm(),
                    latest == null ? null : latest.getInspectionResult().name(),
                    latest == null ? null : latest.getRecordDate()));
        }
        LeaseApi.LeaseAssetReportSummary summary = new LeaseApi.LeaseAssetReportSummary(contracts.size(), active,
                attention, money(activeMonthlyRent), money(generatedRent), money(receivedRent),
                money(outstandingRent), money(depositAvailable), money(totalPurchaseCost),
                money(totalAccumulatedDepreciation), money(totalNetValue));
        return new LeaseApi.LeaseAssetReportView(Instant.now(), summary, rows);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
