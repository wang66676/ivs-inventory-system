package com.ivs.lease;

import com.ivs.lease.api.LeaseApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 电池租赁：合同、资产转租赁池和月租应收。 */
@Service
public class LeaseApplicationService {
    private final LeaseContractRepository contractRepository;
    private final LeaseScheduleRepository scheduleRepository;
    private final LeaseReminderLogRepository reminderLogRepository;
    private final LeaseMasterDataPort masterData;
    private final LeaseReceivablePort receivables;
    private final LeaseNotificationPort notifications;

    public LeaseApplicationService(LeaseContractRepository contractRepository,
                                   LeaseScheduleRepository scheduleRepository,
                                   LeaseReminderLogRepository reminderLogRepository,
                                   LeaseMasterDataPort masterData,
                                   LeaseReceivablePort receivables,
                                   LeaseNotificationPort notifications) {
        this.contractRepository = contractRepository;
        this.scheduleRepository = scheduleRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.masterData = masterData;
        this.receivables = receivables;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<LeaseApi.LeaseContractView> list() {
        return contractRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public LeaseApi.LeaseContractView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public LeaseApi.LeaseContractView create(LeaseApi.CreateLeaseCommand command) {
        LeaseMasterDataPort.SerialInfo serial = masterData.requireSerial(command.serialId());
        if (!isBattery(serial.categoryCode(), serial.categoryName())) {
            throw new ConflictException("LEASE_SERIAL_NOT_BATTERY", "只有动力电池可以建立租赁合同");
        }
        if (!"IN_STOCK".equals(serial.status())) {
            throw new ConflictException("LEASE_SERIAL_NOT_AVAILABLE", "电池必须处于在库状态才能起租");
        }
        if (serial.warehouseId() == null) {
            throw new ConflictException("LEASE_WAREHOUSE_REQUIRED", "电池没有入库仓库，不能起租");
        }
        if (contractRepository.existsBySerialIdAndStatus(serial.id(), LeaseStatus.ACTIVE)) {
            throw new ConflictException("LEASE_ACTIVE_EXISTS", "该电池已存在生效中的租赁合同");
        }
        LeaseMasterDataPort.CustomerInfo customer = masterData.requireCustomer(command.customerId());
        LeaseContract contract;
        try {
            contract = LeaseContract.create("LEASE-%06d".formatted(contractRepository.nextContractSequence()),
                    customer.id(), serial.id(), serial.warehouseId(), command.startDate(), command.endDate(),
                    command.monthlyFee(), command.deposit(), command.ownershipAtEnd(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("LEASE_CONTRACT_INVALID", exception.getMessage());
        }
        LeaseContract saved = contractRepository.save(contract);
        masterData.activateAsset(serial, saved.getId(), saved.getContractNo(), saved.getStartDate());
        generateSchedules(saved);
        return toView(saved);
    }

    @Transactional
    public LeaseApi.LeaseContractView generateDue(UUID contractId, LocalDate throughDate) {
        LeaseContract contract = find(contractId);
        if (contract.getStatus() != LeaseStatus.ACTIVE) {
            throw new ConflictException("LEASE_STATUS_INVALID", "只有生效中的合同可以生成月租应收");
        }
        LocalDate cutoff = throughDate == null ? LocalDate.now() : throughDate;
        generateDueForContract(contract, cutoff);
        return toView(contract);
    }

    /** 批量生成所有生效合同的到期月租应收，重复执行不会重复生成。 */
    @Transactional
    public LeaseApi.DueGenerationResult generateDueBatch(LocalDate throughDate) {
        LocalDate cutoff = throughDate == null ? LocalDate.now() : throughDate;
        List<LeaseContract> contracts = contractRepository.findByStatusOrderByCreatedAtAsc(LeaseStatus.ACTIVE);
        int generated = 0;
        for (LeaseContract contract : contracts) {
            generated += generateDueForContract(contract, cutoff);
        }
        return new LeaseApi.DueGenerationResult(cutoff, contracts.size(), generated);
    }

    /** 月租自动化：生成到期应收、标记逾期、发送即将到期和逾期提醒。 */
    @Transactional
    public LeaseApi.AutomationResult runAutomation(LocalDate asOfDate, Integer reminderDays) {
        LocalDate today = asOfDate == null ? LocalDate.now() : asOfDate;
        int days = reminderDays == null ? 7 : Math.max(1, Math.min(reminderDays, 365));
        List<LeaseContract> contracts = contractRepository.findByStatusOrderByCreatedAtAsc(LeaseStatus.ACTIVE);
        Map<UUID, LeaseContract> activeContracts = new HashMap<>();
        int generated = 0;
        for (LeaseContract contract : contracts) {
            activeContracts.put(contract.getId(), contract);
            generated += generateDueForContract(contract, today);
        }
        Map<UUID, LeaseContract> reminderContracts = new HashMap<>();
        for (LeaseContract contract : contractRepository.findAllByOrderByCreatedAtDesc()) {
            if (contract.getStatus() != LeaseStatus.CANCELLED) {
                reminderContracts.put(contract.getId(), contract);
            }
        }

        int dueSoon = 0;
        for (LeaseSchedule schedule : scheduleRepository
                .findByDueDateBetweenOrderByDueDateAsc(today, today.plusDays(days))) {
            LeaseContract contract = activeContracts.get(schedule.getContractId());
            if (contract == null) continue;
            LeaseReceivablePort.ReceivableInfo receivable = receivableOf(schedule);
            if (receivable != null && (receivable.outstanding().signum() <= 0 || "SETTLED".equals(receivable.status()))) {
                continue;
            }
            if (notifyOnce(contract, schedule, LeaseReminderType.DUE_SOON, today,
                    "租赁月租即将到期", dueSoonContent(contract, schedule, today))) {
                dueSoon++;
            }
        }

        int overdue = 0;
        for (LeaseSchedule schedule : scheduleRepository
                .findByDueDateBeforeAndReceivableIdIsNotNullOrderByDueDateAsc(today)) {
            LeaseContract contract = reminderContracts.get(schedule.getContractId());
            if (contract == null) continue;
            receivables.markOverdue(schedule.getReceivableId(), today);
            LeaseReceivablePort.ReceivableInfo receivable = receivables.get(schedule.getReceivableId());
            if (receivable.outstanding().signum() <= 0) continue;
            if (notifyOnce(contract, schedule, LeaseReminderType.OVERDUE, today,
                    "租赁月租已逾期", overdueContent(contract, schedule, receivable, today))) {
                overdue++;
            }
        }
        return new LeaseApi.AutomationResult(today, days, contracts.size(), generated, dueSoon, overdue);
    }

    @Transactional
    public LeaseApi.LeaseContractView end(UUID contractId) {
        LeaseContract contract = find(contractId);
        if (contract.getOwnershipAtEnd() != LeaseOwnershipAtEnd.RETURN) {
            throw new ConflictException("LEASE_END_MODE_UNSUPPORTED", "续租和买断流程将在后续增量中实现");
        }
        masterData.endLeaseAsset(masterData.requireSerial(contract.getSerialId()), contract.getId(), contract.getContractNo());
        contract.end();
        return toView(contractRepository.save(contract));
    }

    private void generateSchedules(LeaseContract contract) {
        long months = ChronoUnit.MONTHS.between(contract.getStartDate(), contract.getEndDate());
        if (months <= 0) months = 1;
        BigDecimal amount = contract.getMonthlyFee().setScale(2, RoundingMode.HALF_UP);
        List<LeaseSchedule> schedules = new ArrayList<>();
        for (int period = 1; period <= months; period++) {
            schedules.add(LeaseSchedule.create(contract.getId(), period,
                    contract.getStartDate().plusMonths(period), amount));
        }
        scheduleRepository.saveAll(schedules);
    }

    private int generateDueForContract(LeaseContract contract, LocalDate cutoff) {
        int generated = 0;
        for (LeaseSchedule schedule : scheduleRepository
                .findByContractIdAndDueDateLessThanEqualAndReceivableIdIsNullOrderByPeriodNoAsc(contract.getId(), cutoff)) {
            UUID receivableId = receivables.createLeaseReceivable(contract.getCustomerId(), schedule.getAmount(),
                    schedule.getDueDate(), contract.getId(), contract.getContractNo(), schedule.getPeriodNo());
            schedule.bindReceivable(receivableId);
            scheduleRepository.save(schedule);
            generated++;
        }
        return generated;
    }

    private LeaseReceivablePort.ReceivableInfo receivableOf(LeaseSchedule schedule) {
        return schedule.getReceivableId() == null ? null : receivables.get(schedule.getReceivableId());
    }

    private boolean notifyOnce(LeaseContract contract, LeaseSchedule schedule, LeaseReminderType type,
                               LocalDate reminderDate, String title, String content) {
        if (reminderLogRepository.existsByScheduleIdAndReminderTypeAndReminderDate(
                schedule.getId(), type, reminderDate)) {
            return false;
        }
        reminderLogRepository.save(LeaseReminderLog.create(schedule.getId(), type, reminderDate));
        notifications.notifyLeaseReminder(title, content, contract.getId());
        return true;
    }

    private String dueSoonContent(LeaseContract contract, LeaseSchedule schedule, LocalDate today) {
        LeaseMasterDataPort.CustomerInfo customer = masterData.requireCustomer(contract.getCustomerId());
        LeaseMasterDataPort.SerialInfo serial = masterData.requireSerial(contract.getSerialId());
        long days = ChronoUnit.DAYS.between(today, schedule.getDueDate());
        String timing = days == 0 ? "今天到期" : days + " 天后到期";
        return "合同 " + contract.getContractNo() + "，客户 " + customer.name() + "，电池 SN " + serial.serialNo()
                + "，第 " + schedule.getPeriodNo() + " 期月租 " + schedule.getAmount().toPlainString()
                + " 元，" + timing + "（" + schedule.getDueDate() + "）。";
    }

    private String overdueContent(LeaseContract contract, LeaseSchedule schedule,
                                  LeaseReceivablePort.ReceivableInfo receivable, LocalDate today) {
        LeaseMasterDataPort.CustomerInfo customer = masterData.requireCustomer(contract.getCustomerId());
        LeaseMasterDataPort.SerialInfo serial = masterData.requireSerial(contract.getSerialId());
        long overdueDays = ChronoUnit.DAYS.between(schedule.getDueDate(), today);
        return "合同 " + contract.getContractNo() + "，客户 " + customer.name() + "，电池 SN " + serial.serialNo()
                + "，第 " + schedule.getPeriodNo() + " 期月租已逾期 " + overdueDays + " 天，未收 "
                + receivable.outstanding().toPlainString() + " 元。";
    }

    private LeaseContract find(UUID id) {
        return contractRepository.findById(id).orElseThrow(() -> new NotFoundException("租赁合同", id));
    }

    private LeaseApi.LeaseContractView toView(LeaseContract contract) {
        LeaseMasterDataPort.SerialInfo serial = masterData.requireSerial(contract.getSerialId());
        LeaseMasterDataPort.CustomerInfo customer = masterData.requireCustomer(contract.getCustomerId());
        List<LeaseApi.LeaseScheduleView> schedules = scheduleRepository
                .findByContractIdOrderByPeriodNoAsc(contract.getId()).stream()
                .map(schedule -> {
                    LeaseReceivablePort.ReceivableInfo receivable = schedule.getReceivableId() == null ? null
                            : receivables.get(schedule.getReceivableId());
                    return LeaseApi.LeaseScheduleView.from(schedule, receivable);
                }).toList();
        return LeaseApi.LeaseContractView.from(contract, customer.name(), serial, schedules);
    }

    private boolean isBattery(String code, String name) {
        String normalized = code == null ? "" : code.toUpperCase();
        return normalized.equals("BATTERY") || normalized.startsWith("BATTERY-")
                || (name != null && name.contains("电池"));
    }
}
