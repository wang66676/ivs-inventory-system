package com.ivs.lease;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 租赁资产按月直线折旧。 */
@Service
public class LeaseDepreciationService {
    private final LeaseContractRepository contractRepository;
    private final LeaseDepreciationScheduleRepository scheduleRepository;
    private final int depreciationMonths;

    public LeaseDepreciationService(LeaseContractRepository contractRepository,
                                    LeaseDepreciationScheduleRepository scheduleRepository,
                                    @Value("${ivs.lease.depreciation.months:60}") int depreciationMonths) {
        this.contractRepository = contractRepository;
        this.scheduleRepository = scheduleRepository;
        this.depreciationMonths = Math.max(1, depreciationMonths);
    }

    @Transactional
    public void ensureAll() {
        for (LeaseContract contract : contractRepository.findAllByOrderByCreatedAtDesc()) {
            ensureSchedule(contract);
        }
    }

    @Transactional
    public void ensureSchedule(LeaseContract contract) {
        if (contract.getPurchaseCost() == null || contract.getPurchaseCost().signum() <= 0
                || scheduleRepository.existsByContractId(contract.getId())) {
            return;
        }
        BigDecimal cost = money(contract.getPurchaseCost());
        BigDecimal monthly = cost.divide(BigDecimal.valueOf(depreciationMonths), 2, RoundingMode.HALF_UP);
        BigDecimal accumulated = BigDecimal.ZERO;
        List<LeaseDepreciationSchedule> schedules = new ArrayList<>();
        for (int period = 1; period <= depreciationMonths; period++) {
            BigDecimal amount = period == depreciationMonths ? cost.subtract(accumulated) : monthly;
            accumulated = accumulated.add(amount);
            schedules.add(LeaseDepreciationSchedule.create(contract.getId(), period,
                    contract.getStartDate().plusMonths(period), amount, accumulated, cost.subtract(accumulated)));
        }
        scheduleRepository.saveAll(schedules);
    }

    @Transactional(readOnly = true)
    public Summary summary(LeaseContract contract) {
        List<LeaseDepreciationSchedule> schedules = scheduleRepository
                .findByContractIdOrderByPeriodNoAsc(contract.getId());
        LocalDate today = LocalDate.now();
        LeaseDepreciationSchedule current = null;
        for (LeaseDepreciationSchedule schedule : schedules) {
            if (!schedule.getDepreciationDate().isAfter(today)) current = schedule;
        }
        BigDecimal cost = money(contract.getPurchaseCost());
        BigDecimal monthly = schedules.isEmpty()
                ? cost.divide(BigDecimal.valueOf(depreciationMonths), 2, RoundingMode.HALF_UP)
                : schedules.get(0).getAmount();
        return new Summary(cost, monthly,
                current == null ? BigDecimal.ZERO : current.getAccumulatedAmount(),
                current == null ? cost : current.getNetValue(), depreciationMonths);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public record Summary(BigDecimal purchaseCost, BigDecimal monthlyDepreciation,
                          BigDecimal accumulatedDepreciation, BigDecimal netValue, int depreciationMonths) {
    }
}
