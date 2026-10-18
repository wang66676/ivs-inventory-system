package com.ivs.procurement.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.procurement.payable.PayableService;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 采购单：建档、审核、分批收货（序列号商品按 VIN 收货，数量商品按数量收货）。
 *
 * <p>收货在一个事务内完成三件事：写收货单与推进采购单进度、通过 {@link InventoryReceiptPort}
 * 落库存台账与余额、发布 {@code PurchaseReceived} 事件（供后续应付/结算订阅）。</p>
 */
@Service
public class PurchaseOrderApplicationService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderLinePolicyRepository linePolicyRepository;
    private final MasterDataQueryPort masterData;
    private final PurchasePolicyPort policyPort;
    private final InventoryReceiptPort inventoryReceiptPort;
    private final RebateOffsetPort rebateOffsetPort;
    private final PayableService payableService;
    private final EventPublisher events;
    private final CurrentActorProvider actors;
    private final ObjectMapper objectMapper;

    public PurchaseOrderApplicationService(PurchaseOrderRepository orderRepository,
                                           PurchaseOrderLineRepository lineRepository,
                                           PurchaseReceiptRepository receiptRepository,
                                           PurchaseReceiptLineRepository receiptLineRepository,
                                           PurchaseOrderLinePolicyRepository linePolicyRepository,
                                           MasterDataQueryPort masterData,
                                           PurchasePolicyPort policyPort,
                                           InventoryReceiptPort inventoryReceiptPort,
                                           RebateOffsetPort rebateOffsetPort,
                                           PayableService payableService,
                                           EventPublisher events,
                                           CurrentActorProvider actors,
                                           ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.receiptRepository = receiptRepository;
        this.receiptLineRepository = receiptLineRepository;
        this.linePolicyRepository = linePolicyRepository;
        this.masterData = masterData;
        this.policyPort = policyPort;
        this.inventoryReceiptPort = inventoryReceiptPort;
        this.rebateOffsetPort = rebateOffsetPort;
        this.payableService = payableService;
        this.events = events;
        this.actors = actors;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProcurementApi.PurchaseOrderView> list(String keyword, PurchaseOrderStatus status) {
        List<PurchaseOrder> orders;
        if (keyword != null && !keyword.isBlank()) {
            orders = orderRepository.searchByOrderNo("%" + keyword.trim().toLowerCase() + "%");
        } else if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orders.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ProcurementApi.PurchaseOrderView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public ProcurementApi.PurchaseOrderView create(ProcurementApi.SavePurchaseOrderCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("PURCHASE_LINE_REQUIRED", "采购单至少需要一行明细");
        }
        masterData.requireSupplier(command.supplierId());
        if (command.warehouseId() != null) {
            masterData.requireWarehouse(command.warehouseId());
        }
        String orderNo = "PO-%06d".formatted(orderRepository.nextOrderSequence());
        PurchaseOrder order = orderRepository.save(PurchaseOrder.create(orderNo, command.externalOrderNo(),
                command.supplierId(), command.warehouseId(), BigDecimal.ZERO, command.expectedDate(),
                command.remark(), currentUserId()));
        int lineNo = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (ProcurementApi.SavePurchaseOrderLineCommand line : command.lines()) {
            masterData.requireProduct(line.productId());
            PurchaseOrderLine entity;
            try {
                entity = PurchaseOrderLine.create(order.getId(), lineNo++, line.productId(), line.quantity(),
                        line.unitPrice(), line.taxRate(), line.remark());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("PURCHASE_LINE_INVALID", exception.getMessage());
            }
            lineRepository.save(entity);
            saveLinePolicies(entity.getId(), line.policyIds(), command.supplierId());
            total = total.add(entity.getAmount());
        }
        order.updateTotal(total);
        orderRepository.save(order);
        return toView(order);
    }

    @Transactional
    public ProcurementApi.PurchaseOrderView approve(UUID id) {
        PurchaseOrder order = find(id);
        try {
            order.approve();
        } catch (IllegalStateException exception) {
            throw new ConflictException("PURCHASE_STATUS_INVALID", exception.getMessage());
        }
        return toView(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public ProcurementApi.PurchaseOrderView cancel(UUID id) {
        PurchaseOrder order = find(id);
        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("PURCHASE_STATUS_INVALID", exception.getMessage());
        }
        return toView(orderRepository.saveAndFlush(order));
    }

    /** 分批收货：序列号按台收货、数量按数量收货；同一次收货可混合多种产品。 */
    @Transactional
    public ProcurementApi.PurchaseReceiptView receive(UUID orderId, ProcurementApi.ReceivePurchaseOrderCommand command) {
        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("采购单", orderId));
        if (!order.receivable()) {
            throw new ConflictException("PURCHASE_STATUS_INVALID", "当前采购单状态不允许收货: " + order.getStatus());
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("RECEIVE_LINE_REQUIRED", "请至少填写一行收货明细");
        }
        UUID warehouseId = command.warehouseId() != null ? command.warehouseId() : order.getWarehouseId();
        if (warehouseId == null) {
            throw new ConflictException("RECEIVE_WAREHOUSE_REQUIRED", "请指定收货仓库");
        }
        masterData.requireWarehouse(warehouseId);

        List<PurchaseOrderLine> orderLines = lineRepository.findByOrderIdOrderByLineNoAsc(orderId);
        Map<UUID, PurchaseOrderLine> byId = new LinkedHashMap<>();
        orderLines.forEach(line -> byId.put(line.getId(), line));

        // 第一遍：校验并计算本次收货明细与金额
        List<Prepared> prepared = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ProcurementApi.ReceiveLineCommand receiveLine : command.lines()) {
            PurchaseOrderLine orderLine = byId.get(receiveLine.orderLineId());
            if (orderLine == null) {
                throw new NotFoundException("采购明细", receiveLine.orderLineId());
            }
            MasterDataQueryPort.ProductInfo product = masterData.requireProduct(orderLine.getProductId());
            List<String> serialNos = normalizeSerialNos(receiveLine.serialNos());
            BigDecimal quantity;
            if ("SERIAL".equals(product.trackingMode())) {
                if (serialNos.isEmpty()) {
                    throw new ConflictException("RECEIVE_SERIAL_REQUIRED",
                            "商品「" + product.name() + "」按序列号管理，请填写本次到店的 VIN / 电池 SN");
                }
                quantity = BigDecimal.valueOf(serialNos.size());
            } else {
                quantity = receiveLine.quantity() == null ? BigDecimal.ZERO : receiveLine.quantity();
                if (serialNos.size() > 0) {
                    throw new ConflictException("RECEIVE_SERIAL_NOT_ALLOWED",
                            "商品「" + product.name() + "」按数量管理，请填写数量而不是序列号");
                }
            }
            if (quantity.signum() <= 0) {
                throw new ConflictException("RECEIVE_QUANTITY_REQUIRED", "收货数量必须大于 0");
            }
            if (quantity.compareTo(orderLine.remaining()) > 0) {
                throw new ConflictException("RECEIVE_QUANTITY_EXCEEDED", "商品「" + product.name()
                        + "」收货数量超过剩余可收：" + orderLine.remaining().toPlainString());
            }
            BigDecimal unitPrice = receiveLine.unitPrice() == null ? orderLine.getUnitPrice()
                    : receiveLine.unitPrice().setScale(2, RoundingMode.HALF_UP);
            prepared.add(new Prepared(orderLine, product, quantity, unitPrice, receiveLine.batchNo(), serialNos));
            total = total.add(quantity.multiply(unitPrice));
        }

        // 第二遍：落收货单、推进采购单进度
        String receiptNo = "RC-%06d".formatted(receiptRepository.nextReceiptSequence());
        PurchaseReceipt receipt = receiptRepository.save(PurchaseReceipt.create(receiptNo, order.getId(),
                order.getSupplierId(), warehouseId, total, command.remark()));
        List<InventoryReceiptPort.ReceiptPostingLine> postingLines = new ArrayList<>();
        for (Prepared item : prepared) {
            item.orderLine().receive(item.quantity());
            lineRepository.save(item.orderLine());
            receiptLineRepository.save(PurchaseReceiptLine.create(receipt.getId(), item.orderLine().getId(),
                    item.product().id(), item.quantity(), item.unitPrice(), item.batchNo(),
                    serialNosJson(item.serialNos()), null));
            postingLines.add(new InventoryReceiptPort.ReceiptPostingLine(item.product().id(), item.quantity(),
                    item.unitPrice(), item.batchNo(), item.serialNos()));
        }

        // 落库存：由 app 层适配器调用库存引擎（含序列号状态流转）
        inventoryReceiptPort.postReceipt(new InventoryReceiptPort.ReceiptPosting(receipt.getId(), receiptNo,
                warehouseId, postingLines));

        boolean fullyReceived = orderLines.stream().allMatch(PurchaseOrderLine::fullyReceived);
        order.refreshReceiveStatus(fullyReceived);
        orderRepository.save(order);

        // 入库单确认时用同一厂家的已到账返利抵扣车款（无需审批）
        if (command.offsetTxnIds() != null && !command.offsetTxnIds().isEmpty()) {
            BigDecimal applied = rebateOffsetPort.offset(order.getSupplierId(), order.getId(), receipt.getId(),
                    receiptNo, total, command.offsetTxnIds());
            receipt.applyOffset(applied);
        }
        receipt.markPosted();
        receiptRepository.save(receipt);
        payableService.createFromReceipt(order.getSupplierId(), order.getId(), order.getOrderNo(),
                receipt.getId(), receiptNo, receipt.netAmount());

        publishReceived(receipt, order, postingLines.size());
        return toReceiptView(receipt);
    }

    @Transactional(readOnly = true)
    public List<ProcurementApi.PurchaseReceiptView> listReceipts() {
        return receiptRepository.findAllByOrderByReceivedAtDesc().stream().map(this::toReceiptView).toList();
    }

    @Transactional(readOnly = true)
    public ProcurementApi.PurchaseReceiptView getReceipt(UUID id) {
        return receiptRepository.findById(id).map(this::toReceiptView)
                .orElseThrow(() -> new NotFoundException("收货单", id));
    }

    private record Prepared(PurchaseOrderLine orderLine, MasterDataQueryPort.ProductInfo product,
                            BigDecimal quantity, BigDecimal unitPrice, String batchNo, List<String> serialNos) {
    }

    private List<String> normalizeSerialNos(List<String> serialNos) {
        if (serialNos == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String serialNo : serialNos) {
            if (serialNo == null || serialNo.isBlank()) {
                continue;
            }
            String value = serialNo.trim().toUpperCase();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String serialNosJson(List<String> serialNos) {
        if (serialNos == null || serialNos.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(serialNos);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> parseSerialNos(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void publishReceived(PurchaseReceipt receipt, PurchaseOrder order, int lineCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptId", receipt.getId());
        payload.put("receiptNo", receipt.getReceiptNo());
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("supplierId", order.getSupplierId());
        payload.put("warehouseId", receipt.getWarehouseId());
        payload.put("amount", receipt.getAmount());
        payload.put("lineCount", lineCount);
        events.publish(new EventEnvelope(UUID.randomUUID(), "PurchaseReceived", "PurchaseReceipt",
                receipt.getId(), receipt.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }

    private void saveLinePolicies(UUID lineId, List<UUID> policyIds, UUID supplierId) {
        if (policyIds == null || policyIds.isEmpty()) return;
        LinkedHashSet<UUID> orderedIds = new LinkedHashSet<>();
        for (UUID policyId : policyIds) {
            if (policyId != null) orderedIds.add(policyId);
        }
        if (orderedIds.isEmpty()) return;

        Map<UUID, PurchasePolicyPort.PolicyInfo> found = new LinkedHashMap<>();
        for (PurchasePolicyPort.PolicyInfo info : policyPort.findByIds(orderedIds)) {
            found.put(info.id(), info);
        }
        int sortNo = 0;
        for (UUID policyId : orderedIds) {
            PurchasePolicyPort.PolicyInfo info = found.get(policyId);
            if (info == null) {
                throw new ConflictException("PURCHASE_POLICY_NOT_FOUND", "返利政策不存在：" + policyId);
            }
            if (!supplierId.equals(info.supplierId())) {
                throw new ConflictException("PURCHASE_POLICY_SUPPLIER_MISMATCH",
                        "返利政策「" + info.name() + "」不属于当前采购单厂家");
            }
            if (!"PUBLISHED".equals(info.status())) {
                throw new ConflictException("PURCHASE_POLICY_NOT_AVAILABLE",
                        "返利政策「" + info.name() + "」尚未发布或已失效");
            }
            linePolicyRepository.save(PurchaseOrderLinePolicy.create(lineId, policyId, sortNo++));
        }
    }

    private PurchaseOrder find(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("采购单", id));
    }

    private ProcurementApi.PurchaseOrderView toView(PurchaseOrder order) {
        MasterDataQueryPort.SupplierInfo supplier = masterData.requireSupplier(order.getSupplierId());
        MasterDataQueryPort.WarehouseInfo warehouse = order.getWarehouseId() == null ? null
                : masterData.requireWarehouse(order.getWarehouseId());

        List<PurchaseOrderLine> lineEntities = lineRepository.findByOrderIdOrderByLineNoAsc(order.getId());
        List<UUID> lineIds = lineEntities.stream().map(PurchaseOrderLine::getId).toList();
        Map<UUID, List<UUID>> policyIdsByLine = new LinkedHashMap<>();
        Set<UUID> allPolicyIds = new LinkedHashSet<>();
        if (!lineIds.isEmpty()) {
            for (PurchaseOrderLinePolicy link : linePolicyRepository
                    .findByOrderLineIdInOrderByOrderLineIdAscSortNoAsc(lineIds)) {
                policyIdsByLine.computeIfAbsent(link.getOrderLineId(), key -> new ArrayList<>())
                        .add(link.getPolicyId());
                allPolicyIds.add(link.getPolicyId());
            }
        }
        Map<UUID, PurchasePolicyPort.PolicyInfo> policyById = new LinkedHashMap<>();
        if (!allPolicyIds.isEmpty()) {
            for (PurchasePolicyPort.PolicyInfo info : policyPort.findByIds(allPolicyIds)) {
                policyById.put(info.id(), info);
            }
        }

        List<ProcurementApi.PurchaseOrderLineView> lines = new ArrayList<>();
        for (PurchaseOrderLine line : lineEntities) {
            MasterDataQueryPort.ProductInfo product = masterData.requireProduct(line.getProductId());
            List<ProcurementApi.PolicyRefView> policies = new ArrayList<>();
            for (UUID policyId : policyIdsByLine.getOrDefault(line.getId(), List.of())) {
                PurchasePolicyPort.PolicyInfo info = policyById.get(policyId);
                if (info != null) {
                    policies.add(new ProcurementApi.PolicyRefView(info.id(), info.policyNo(), info.name()));
                }
            }
            lines.add(new ProcurementApi.PurchaseOrderLineView(line.getId(), line.getLineNo(),
                    line.getProductId(), product.productNo(), product.name(), product.series(), product.configCode(),
                    product.categoryCode(), product.trackingMode(), line.getQuantity(), line.getReceivedQuantity(),
                    line.remaining(), line.getUnitPrice(), line.getTaxRate(), line.getAmount(),
                    line.getRemark(), policies));
        }
        return ProcurementApi.PurchaseOrderView.from(order,
                new ProcurementApi.SupplierInfoView(supplier.id(), supplier.name(), supplier.supplierType()),
                warehouse == null ? null : new ProcurementApi.WarehouseInfoView(warehouse.id(), warehouse.name()),
                lines);
    }

    private ProcurementApi.PurchaseReceiptView toReceiptView(PurchaseReceipt receipt) {
        String orderNo = orderRepository.findById(receipt.getOrderId()).map(PurchaseOrder::getOrderNo).orElse(null);
        String supplierName = masterData.requireSupplier(receipt.getSupplierId()).name();
        String warehouseName = receipt.getWarehouseId() == null ? null
                : masterData.requireWarehouse(receipt.getWarehouseId()).name();
        List<ProcurementApi.PurchaseReceiptLineView> lines = receiptLineRepository.findByReceiptId(receipt.getId())
                .stream().map(line -> {
                    MasterDataQueryPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                    return new ProcurementApi.PurchaseReceiptLineView(line.getId(), line.getProductId(),
                            product.productNo(), product.name(), line.getQuantity(), line.getUnitPrice(),
                            line.getBatchNo(), parseSerialNos(line.getSerialNos()));
                }).toList();
        return ProcurementApi.PurchaseReceiptView.from(receipt, orderNo, supplierName, warehouseName, lines);
    }
}








