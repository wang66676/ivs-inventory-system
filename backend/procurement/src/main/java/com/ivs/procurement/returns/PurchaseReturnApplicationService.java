package com.ivs.procurement.returns;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.procurement.payable.PayableService;
import com.ivs.procurement.purchase.MasterDataQueryPort;
import com.ivs.procurement.returns.api.PurchaseReturnApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 采购退货：草稿录入、过账出库并冲减采购金额。 */
@Service
public class PurchaseReturnApplicationService {

    private final PurchaseReturnRepository returnRepository;
    private final PurchaseReturnLineRepository lineRepository;
    private final PurchaseReturnStockPort stockPort;
    private final PayableService payableService;
    private final MasterDataQueryPort masterData;
    private final CurrentActorProvider actors;
    private final ObjectMapper objectMapper;

    public PurchaseReturnApplicationService(PurchaseReturnRepository returnRepository,
                                            PurchaseReturnLineRepository lineRepository,
                                            PurchaseReturnStockPort stockPort,
                                            PayableService payableService,
                                            MasterDataQueryPort masterData,
                                            CurrentActorProvider actors,
                                            ObjectMapper objectMapper) {
        this.returnRepository = returnRepository;
        this.lineRepository = lineRepository;
        this.stockPort = stockPort;
        this.payableService = payableService;
        this.masterData = masterData;
        this.actors = actors;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnApi.ReturnView> list(UUID supplierId, PurchaseReturnStatus status) {
        List<PurchaseReturn> rows = supplierId != null
                ? returnRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId)
                : status != null ? returnRepository.findByStatusOrderByCreatedAtDesc(status)
                : returnRepository.findAllByOrderByCreatedAtDesc();
        return rows.stream().filter(row -> status == null || row.getStatus() == status)
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseReturnApi.ReturnView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public PurchaseReturnApi.ReturnView create(PurchaseReturnApi.CreateReturnCommand command) {
        if (command == null || command.supplierId() == null || command.warehouseId() == null) {
            throw new ConflictException("PURCHASE_RETURN_REQUIRED", "请选择供应商与退货仓库");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("PURCHASE_RETURN_LINE_REQUIRED", "采购退货至少需要一条明细");
        }
        masterData.requireSupplier(command.supplierId());
        masterData.requireWarehouse(command.warehouseId());
        PurchaseReturn value = PurchaseReturn.create(
                "PR-%06d".formatted(returnRepository.nextReturnSequence()), command.supplierId(),
                command.warehouseId(), command.reason(), command.remark(), currentUserId());
        returnRepository.save(value);
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReturnApi.SaveLineCommand line : command.lines()) {
            masterData.requireProduct(line.productId());
            PurchaseReturnLine entity;
            try {
                entity = PurchaseReturnLine.create(value.getId(), line.productId(), line.quantity(),
                        line.unitPrice(), line.batchNo(), serialNosJson(line.serialNos()));
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("PURCHASE_RETURN_LINE_INVALID", exception.getMessage());
            }
            lineRepository.save(entity);
            total = total.add(entity.getAmount());
        }
        try {
            value.total(total);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PURCHASE_RETURN_AMOUNT_INVALID", exception.getMessage());
        }
        return toView(returnRepository.save(value));
    }

    @Transactional
    public PurchaseReturnApi.ReturnView post(UUID id) {
        PurchaseReturn value = find(id);
        if (value.getStatus() != PurchaseReturnStatus.DRAFT) {
            throw new ConflictException("PURCHASE_RETURN_STATUS_INVALID", "只有草稿退货单可以过账");
        }
        List<PurchaseReturnLine> lines = lineRepository.findByReturnIdOrderByCreatedAtAsc(id);
        stockPort.postReturn(new PurchaseReturnStockPort.Posting(value.getId(), value.getReturnNo(),
                value.getWarehouseId(), lines.stream().map(line -> new PurchaseReturnStockPort.PostingLine(
                        line.getProductId(), line.getQuantity(), line.getBatchNo(),
                        parseSerialNos(line.getSerialNos()))).toList()));
        try {
            value.post(currentUserId());
        } catch (IllegalStateException exception) {
            throw new ConflictException("PURCHASE_RETURN_STATUS_INVALID", exception.getMessage());
        }
        payableService.applySupplierReturn(value.getSupplierId(), value.getTotalAmount());
        return toView(returnRepository.save(value));
    }

    @Transactional
    public PurchaseReturnApi.ReturnView cancel(UUID id) {
        PurchaseReturn value = find(id);
        try {
            value.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("PURCHASE_RETURN_STATUS_INVALID", exception.getMessage());
        }
        return toView(returnRepository.save(value));
    }

    private PurchaseReturnApi.ReturnView toView(PurchaseReturn value) {
        String supplierName = masterData.requireSupplier(value.getSupplierId()).name();
        String warehouseName = masterData.requireWarehouse(value.getWarehouseId()).name();
        List<PurchaseReturnApi.LineView> lines = lineRepository.findByReturnIdOrderByCreatedAtAsc(value.getId())
                .stream().map(line -> {
                    MasterDataQueryPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                    return PurchaseReturnApi.LineView.from(line, product.productNo(), product.name(),
                            parseSerialNos(line.getSerialNos()));
                }).toList();
        return PurchaseReturnApi.ReturnView.from(value, supplierName, warehouseName, lines);
    }

    private PurchaseReturn find(UUID id) {
        return returnRepository.findById(id).orElseThrow(() -> new NotFoundException("采购退货单", id));
    }

    private String serialNosJson(List<String> serialNos) {
        if (serialNos == null || serialNos.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(serialNos.stream().map(String::trim)
                    .filter(value -> !value.isBlank()).map(String::toUpperCase).toList());
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> parseSerialNos(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }
}



