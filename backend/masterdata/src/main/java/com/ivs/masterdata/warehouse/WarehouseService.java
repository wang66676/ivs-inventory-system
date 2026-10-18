package com.ivs.masterdata.warehouse;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.WarehouseView> list() {
        return warehouseRepository.findAllByOrderByCodeAsc().stream().map(MasterdataApi.WarehouseView::from).toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.WarehouseView get(UUID id) {
        return MasterdataApi.WarehouseView.from(find(id));
    }

    @Transactional
    public MasterdataApi.WarehouseView create(MasterdataApi.SaveWarehouseCommand command) {
        String code = command.code() == null ? "" : command.code().trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ConflictException("WAREHOUSE_CODE_REQUIRED", "仓库编码不能为空");
        }
        if (warehouseRepository.existsByCode(code)) {
            throw new ConflictException("WAREHOUSE_CODE_DUPLICATE", "仓库编码已存在：" + code);
        }
        Warehouse warehouse = Warehouse.create(code, command.name(), command.type(), command.address(),
                command.ownerOrgId());
        return MasterdataApi.WarehouseView.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public MasterdataApi.WarehouseView update(UUID id, MasterdataApi.SaveWarehouseCommand command) {
        Warehouse warehouse = find(id);
        warehouse.update(command.name(), command.type(), command.address(), command.ownerOrgId());
        return MasterdataApi.WarehouseView.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public MasterdataApi.WarehouseView disable(UUID id) {
        Warehouse warehouse = find(id);
        warehouse.disable();
        return MasterdataApi.WarehouseView.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public MasterdataApi.WarehouseView enable(UUID id) {
        Warehouse warehouse = find(id);
        warehouse.enable();
        return MasterdataApi.WarehouseView.from(warehouseRepository.save(warehouse));
    }

    @Transactional(readOnly = true)
    public Warehouse find(UUID id) {
        return warehouseRepository.findById(id).orElseThrow(() -> new NotFoundException("仓库", id));
    }
}