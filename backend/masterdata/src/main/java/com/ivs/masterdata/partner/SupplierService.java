package com.ivs.masterdata.partner;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.SupplierType;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 供应商：整车厂（OEM）是返利申报与资金池的主体。编码留空时自动生成 SUP-000001。 */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.SupplierView> list(SupplierType supplierType) {
        List<Supplier> suppliers = supplierType == null
                ? supplierRepository.findAllByOrderByCodeAsc()
                : supplierRepository.findBySupplierTypeOrderByCodeAsc(supplierType);
        return suppliers.stream().map(MasterdataApi.SupplierView::from).toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.SupplierView get(UUID id) {
        return MasterdataApi.SupplierView.from(find(id));
    }

    @Transactional
    public MasterdataApi.SupplierView create(MasterdataApi.SaveSupplierCommand command) {
        String code = command.code() == null || command.code().isBlank()
                ? "SUP-%06d".formatted(supplierRepository.nextSupplierSequence())
                : command.code().trim().toUpperCase();
        if (supplierRepository.existsByCode(code)) {
            throw new ConflictException("SUPPLIER_CODE_DUPLICATE", "供应商编码已存在：" + code);
        }
        Supplier supplier = Supplier.create(code, command.name(), command.supplierType(), command.contactName(),
                command.contactPhone(), command.taxNo(), command.paymentTerms(), command.remark());
        return MasterdataApi.SupplierView.from(supplierRepository.save(supplier));
    }

    @Transactional
    public MasterdataApi.SupplierView update(UUID id, MasterdataApi.SaveSupplierCommand command) {
        Supplier supplier = find(id);
        supplier.update(command.name(), command.supplierType(), command.contactName(), command.contactPhone(),
                command.taxNo(), command.paymentTerms(), command.remark());
        return MasterdataApi.SupplierView.from(supplierRepository.save(supplier));
    }

    @Transactional
    public MasterdataApi.SupplierView disable(UUID id) {
        Supplier supplier = find(id);
        supplier.disable();
        return MasterdataApi.SupplierView.from(supplierRepository.save(supplier));
    }

    @Transactional
    public MasterdataApi.SupplierView enable(UUID id) {
        Supplier supplier = find(id);
        supplier.enable();
        return MasterdataApi.SupplierView.from(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public Supplier find(UUID id) {
        return supplierRepository.findById(id).orElseThrow(() -> new NotFoundException("供应商", id));
    }
}