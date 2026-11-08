package com.ivs.lease;

import com.ivs.lease.api.LeaseApi;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 租赁电池退役、报废与梯次利用登记。 */
@Service
public class LeaseDisposalService {
    private final LeaseAssetDisposalRepository disposalRepository;
    private final LeaseContractRepository contractRepository;
    private final LeaseMasterDataPort masterData;

    public LeaseDisposalService(LeaseAssetDisposalRepository disposalRepository,
                                LeaseContractRepository contractRepository,
                                LeaseMasterDataPort masterData) {
        this.disposalRepository = disposalRepository;
        this.contractRepository = contractRepository;
        this.masterData = masterData;
    }

    @Transactional(readOnly = true)
    public List<LeaseApi.AssetDisposalView> list(UUID contractId) {
        List<LeaseAssetDisposal> records = contractId == null
                ? disposalRepository.findAllByOrderByDisposalDateDescCreatedAtDesc()
                : disposalRepository.findByContractIdOrderByDisposalDateDescCreatedAtDesc(contractId);
        return records.stream().map(LeaseApi.AssetDisposalView::from).toList();
    }

    @Transactional
    public LeaseApi.AssetDisposalView retire(UUID serialId, LeaseApi.RetireAssetCommand command) {
        LeaseMasterDataPort.SerialInfo serial = masterData.requireSerial(serialId);
        if (!"IN_STOCK".equals(serial.status())) {
            throw new ConflictException("LEASE_DISPOSAL_STATUS_INVALID", "只有已归还且在库的电池可以办理退役处置");
        }
        LeaseContract contract = contractRepository.findFirstBySerialIdOrderByCreatedAtDesc(serialId).orElse(null);
        if (contract != null && contract.getStatus() == LeaseStatus.ACTIVE) {
            throw new ConflictException("LEASE_DISPOSAL_ACTIVE_CONTRACT", "生效中的租赁合同不能办理退役处置");
        }
        if (disposalRepository.existsBySerialId(serialId)) {
            throw new ConflictException("LEASE_DISPOSAL_EXISTS", "该电池已经存在退役处置记录");
        }
        LeaseDispositionType type;
        try {
            type = LeaseDispositionType.valueOf(command.dispositionType());
        } catch (RuntimeException exception) {
            throw new ConflictException("LEASE_DISPOSAL_TYPE_INVALID", "未知的退役处置方式");
        }
        if (type == LeaseDispositionType.SECOND_LIFE
                && (command.destination() == null || command.destination().isBlank())) {
            throw new ConflictException("LEASE_DISPOSAL_DESTINATION_REQUIRED", "梯次利用请填写去向或用途");
        }
        LeaseAssetDisposal disposal;
        try {
            disposal = LeaseAssetDisposal.create("DSP-%06d".formatted(disposalRepository.nextDisposalSequence()),
                    serialId, contract == null ? null : contract.getId(), type, command.disposalDate(),
                    command.sohPercent(), command.destination(), command.reason(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("LEASE_DISPOSAL_INVALID", exception.getMessage());
        }
        LeaseAssetDisposal saved = disposalRepository.save(disposal);
        masterData.retireAsset(serial, saved.getId(), saved.getDisposalNo(), type == LeaseDispositionType.SCRAP,
                saved.getReason());
        return LeaseApi.AssetDisposalView.from(saved);
    }
}
