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
import java.util.List;
import java.util.UUID;

/** 电池租赁：合同、资产转租赁池和月租应收。 */
@Service
public class LeaseApplicationService {
    private final LeaseContractRepository contractRepository;
    private final LeaseScheduleRepository scheduleRepository;
    private final LeaseMasterDataPort masterData;
    private final LeaseReceivablePort receivables;

    public LeaseApplicationService(LeaseContractRepository contractRepository,
                                   LeaseScheduleRepository scheduleRepository,
                                   LeaseMasterDataPort masterData,
                                   LeaseReceivablePort receivables) {
        this.contractRepository = contractRepository;
        this.scheduleRepository = scheduleRepository;
        this.masterData = masterData;
        this.receivables = receivables;
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
        for (LeaseSchedule schedule : scheduleRepository
                .findByContractIdAndDueDateLessThanEqualAndReceivableIdIsNullOrderByPeriodNoAsc(contractId, cutoff)) {
            UUID receivableId = receivables.createLeaseReceivable(contract.getCustomerId(), schedule.getAmount(),
                    schedule.getDueDate(), contract.getId(), contract.getContractNo(), schedule.getPeriodNo());
            schedule.bindReceivable(receivableId);
            scheduleRepository.save(schedule);
        }
        return toView(contract);
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
