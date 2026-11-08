package com.ivs.lease;

import com.ivs.lease.api.LeaseApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 租赁电池健康档案。 */
@Service
public class LeaseHealthService {
    private final LeaseContractRepository contractRepository;
    private final LeaseBatteryHealthRepository healthRepository;

    public LeaseHealthService(LeaseContractRepository contractRepository,
                              LeaseBatteryHealthRepository healthRepository) {
        this.contractRepository = contractRepository;
        this.healthRepository = healthRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaseApi.HealthRecordView> list(UUID contractId) {
        requireContract(contractId);
        return healthRepository.findByContractIdOrderByRecordDateDescCreatedAtDesc(contractId).stream()
                .map(LeaseApi.HealthRecordView::from).toList();
    }

    @Transactional
    public LeaseApi.HealthRecordView create(UUID contractId, LeaseApi.HealthRecordCommand command) {
        LeaseContract contract = requireContract(contractId);
        LeaseInspectionResult result;
        try {
            result = command == null || command.inspectionResult() == null
                    ? LeaseInspectionResult.NORMAL : LeaseInspectionResult.valueOf(command.inspectionResult());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("LEASE_HEALTH_RESULT_INVALID", "未知的检测结论");
        }
        try {
            LeaseBatteryHealth record = LeaseBatteryHealth.create(contractId, contract.getSerialId(),
                    command.recordDate(), command.sohPercent(), command.cycleCount(), command.mileageKm(),
                    result, command.inspector(), command.remark());
            return LeaseApi.HealthRecordView.from(healthRepository.save(record));
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("LEASE_HEALTH_INVALID", exception.getMessage());
        }
    }

    private LeaseContract requireContract(UUID contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("租赁合同", contractId));
    }
}
