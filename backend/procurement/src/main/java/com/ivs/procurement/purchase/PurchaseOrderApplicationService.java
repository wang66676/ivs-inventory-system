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
import java.time.LocalDate;
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
    private final PurchasePreEntitlementPort preEntitlementPort;
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
                                           PurchasePreEntitlementPort preEntitlementPort,
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
        this.preEntitlementPort = preEntitlementPort;
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
        UUID orderWarehouseId = command.lines().isEmpty() ? command.warehouseId()
                : (isBattery(masterData.requireProduct(command.lines().get(0).productId()))
                    ? masterData.requireBatteryWarehouse().id() : command.warehouseId());
        if (!command.lines().isEmpty() && !isBattery(masterData.requireProduct(command.lines().get(0).productId()))
                && command.warehouseId() != null) {
            masterData.requireWarehouse(command.warehouseId());
        }
        String orderNo = "PO-%06d".formatted(orderRepository.nextOrderSequence());
        PurchaseOrder order = orderRepository.save(PurchaseOrder.create(orderNo, command.externalOrderNo(),
                command.supplierId(), orderWarehouseId, BigDecimal.ZERO, command.expectedDate(),
                command.remark(), currentUserId()));
        BigDecimal total = saveLines(order, command.supplierId(), command.lines());
        order.updateTotal(total);
        orderRepository.save(order);
        return toView(order);
    }

    /** 草稿采购单可补录返利政策、收货仓库等明细信息。 */
    @Transactional
    public ProcurementApi.PurchaseOrderView update(UUID id, ProcurementApi.SavePurchaseOrderCommand command) {
        PurchaseOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("采购单", id));
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("PURCHASE_STATUS_INVALID", "只有草稿采购单可以编辑: " + order.getStatus());
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("PURCHASE_LINE_REQUIRED", "采购单至少需要一行明细");
        }
        masterData.requireSupplier(command.supplierId());
        UUID orderWarehouseId = command.lines().isEmpty() ? command.warehouseId()
                : (isBattery(masterData.requireProduct(command.lines().get(0).productId()))
                    ? masterData.requireBatteryWarehouse().id() : command.warehouseId());
        if (!command.lines().isEmpty() && !isBattery(masterData.requireProduct(command.lines().get(0).productId()))
                && command.warehouseId() != null) {
            masterData.requireWarehouse(command.warehouseId());
        }
        try {
            order.updateDraft(command.externalOrderNo(), command.supplierId(), orderWarehouseId,
                    command.expectedDate(), command.remark());
        } catch (IllegalStateException exception) {
            throw new ConflictException("PURCHASE_STATUS_INVALID", exception.getMessage());
        }
        List<UUID> existingLineIds = lineRepository.findByOrderIdOrderByLineNoAsc(order.getId()).stream()
                .map(PurchaseOrderLine::getId).toList();
        if (!existingLineIds.isEmpty()) {
            linePolicyRepository.deleteByOrderLineIdIn(existingLineIds);
        }
        lineRepository.deleteByOrderId(order.getId());
        lineRepository.flush();
        BigDecimal total = saveLines(order, command.supplierId(), command.lines());
        order.updateTotal(total);
        ProcurementApi.PurchaseOrderView view = toView(orderRepository.saveAndFlush(order));
        preEntitlementPort.clear(view.id());
        return view;
    }

    private BigDecimal saveLines(PurchaseOrder order, UUID supplierId,
                                 List<ProcurementApi.SavePurchaseOrderLineCommand> lines) {
        int lineNo = 1;
        BigDecimal total = BigDecimal.ZERO;
        Set<String> seenSerialNos = new LinkedHashSet<>();
        String orderScope = null;
        for (ProcurementApi.SavePurchaseOrderLineCommand line : lines) {
            MasterDataQueryPort.ProductInfo product = masterData.requireProduct(line.productId());
            boolean battery = isBattery(product);
            String lineScope = battery ? "BATTERY" : "VEHICLE";
            if (orderScope != null && !orderScope.equals(lineScope)) {
                throw new ConflictException("PURCHASE_SCOPE_MIXED", "整车与动力电池不能混在同一张采购单中");
            }
            orderScope = lineScope;
            if (battery && line.policyIds() != null && !line.policyIds().isEmpty()) {
                throw new ConflictException("BATTERY_POLICY_NOT_ALLOWED", "动力电池不支持返利政策");
            }
            BatteryPricing batteryPricing = batteryPricing(line, battery);
            UUID effectiveWarehouseId = line.warehouseId();
            if (battery) {
                effectiveWarehouseId = masterData.requireBatteryWarehouse().id();
            } else if (line.warehouseId() != null) {
                masterData.requireWarehouse(line.warehouseId());
            }
            String serialNo = line.serialNo() == null || line.serialNo().isBlank()
                    ? null : line.serialNo().trim().toUpperCase();
            if (serialNo != null) {
                if (!seenSerialNos.add(serialNo)) {
                    throw new ConflictException("PURCHASE_VIN_DUPLICATE", "采购明细 VIN/SN 重复：" + serialNo);
                }
                if (lineRepository.existsBySerialNoIgnoreCase(serialNo)) {
                    throw new ConflictException("PURCHASE_VIN_DUPLICATE", "VIN/SN 已存在于其他采购明细：" + serialNo);
                }
            }
            BigDecimal quantity = serialNo == null ? line.quantity() : BigDecimal.ONE;
            PurchaseOrderLine entity;
            try {
                entity = PurchaseOrderLine.create(order.getId(), lineNo++, line.productId(), quantity,
                        batteryPricing.unitPrice(), line.taxRate(), battery ? null : line.configText(), effectiveWarehouseId, serialNo,
                        line.certificateApplicationDate(), line.arrivalDate(), line.remark(), batteryPricing.boxManufacturer(),
                        batteryPricing.boxModel(), batteryPricing.boxUnitPrice(), batteryPricing.packManufacturer(),
                        batteryPricing.energyKwh(), batteryPricing.packUnitPrice());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("PURCHASE_LINE_INVALID", exception.getMessage());
            }
            lineRepository.save(entity);
            if (!battery) {
                saveLinePolicies(entity.getId(), line.policyIds(), supplierId);
            }
            total = total.add(entity.getAmount());
        }
        return total;
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
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new ConflictException("PURCHASE_ALREADY_RECEIVED", "采购单已全部收货，无需重复收货");
        }
        if (!order.receivable()) {
            throw new ConflictException("PURCHASE_STATUS_INVALID", "当前采购单状态不允许收货: " + order.getStatus());
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("RECEIVE_LINE_REQUIRED", "请至少填写一行收货明细");
        }
        UUID warehouseId = command.warehouseId() != null ? command.warehouseId() : order.getWarehouseId();
        List<PurchaseOrderLine> orderLines = lineRepository.findByOrderIdOrderByLineNoAsc(orderId);
        boolean batteryOrder = "BATTERY".equals(purchaseScope(orderLines));
        if (batteryOrder) {
            warehouseId = masterData.requireBatteryWarehouse().id();
        }
        if (warehouseId != null) {
            masterData.requireWarehouse(warehouseId);
        }
        Map<UUID, PurchaseOrderLine> byId = new LinkedHashMap<>();
        orderLines.forEach(line -> byId.put(line.getId(), line));

        // 第一遍：校验并计算本次收货明细与金额
        List<Prepared> prepared = new ArrayList<>();
        Set<String> requestSerialNos = new LinkedHashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ProcurementApi.ReceiveLineCommand receiveLine : command.lines()) {
            PurchaseOrderLine orderLine = byId.get(receiveLine.orderLineId());
            if (orderLine == null) {
                throw new NotFoundException("采购明细", receiveLine.orderLineId());
            }
            MasterDataQueryPort.ProductInfo product = masterData.requireProduct(orderLine.getProductId());
            UUID lineWarehouseId = batteryOrder ? warehouseId
                    : (receiveLine.warehouseId() != null ? receiveLine.warehouseId()
                    : (orderLine.getWarehouseId() != null ? orderLine.getWarehouseId() : warehouseId));
            if (lineWarehouseId == null) {
                throw new ConflictException("RECEIVE_WAREHOUSE_REQUIRED",
                        "商品「" + product.name() + "」请按本次到货明细指定收货仓库");
            }
            masterData.requireWarehouse(lineWarehouseId);
            List<String> serialNos = normalizeSerialNos(receiveLine.serialNos());
            for (String serialNo : serialNos) {
                if (!requestSerialNos.add(serialNo)) {
                    throw new ConflictException("RECEIVE_SERIAL_DUPLICATE", "有重复项，请核查后再提交");
                }
            }
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
            prepared.add(new Prepared(orderLine, product, lineWarehouseId, quantity, unitPrice,
                    receiveLine.batchNo(), serialNos, receiveLine.certificateApplicationDate()));
            total = total.add(quantity.multiply(unitPrice));
        }

        // 第二遍：落收货单、推进采购单进度
        UUID receiptWarehouseId = warehouseId != null ? warehouseId : prepared.get(0).warehouseId();
        String receiptNo = "RC-%06d".formatted(receiptRepository.nextReceiptSequence());
        PurchaseReceipt receipt = receiptRepository.save(PurchaseReceipt.create(receiptNo, order.getId(),
                order.getSupplierId(), receiptWarehouseId, total, command.remark()));
        List<InventoryReceiptPort.ReceiptPostingLine> postingLines = new ArrayList<>();
        for (Prepared item : prepared) {
            item.orderLine().receive(item.quantity());
            lineRepository.save(item.orderLine());
            receiptLineRepository.save(PurchaseReceiptLine.create(receipt.getId(), item.orderLine().getId(),
                    item.product().id(), item.warehouseId(), item.quantity(), item.unitPrice(), item.batchNo(),
                    serialNosJson(item.serialNos()), item.certificateApplicationDate(), null));
            postingLines.add(new InventoryReceiptPort.ReceiptPostingLine(item.product().id(), item.warehouseId(),
                    item.quantity(), item.unitPrice(), item.batchNo(), item.serialNos(),
                    item.certificateApplicationDate()));
        }

        // 落库存：由 app 层适配器调用库存引擎（含序列号状态流转）
        inventoryReceiptPort.postReceipt(new InventoryReceiptPort.ReceiptPosting(receipt.getId(), receiptNo,
                receiptWarehouseId, postingLines));

        for (Prepared item : prepared) {
            if (item.serialNos().isEmpty()) continue;
            List<UUID> policyIds = linePolicyRepository
                    .findByOrderLineIdOrderBySortNoAsc(item.orderLine().getId()).stream()
                    .map(PurchaseOrderLinePolicy::getPolicyId).toList();
            if (!isBattery(item.product()) && !policyIds.isEmpty()) {
                preEntitlementPort.syncSerials(order.getId(), item.orderLine().getId(), item.product().id(),
                        item.unitPrice(), policyIds, item.serialNos());
            }
        }

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

    /** 通过 VIN/SN 反查其采购收货所关联的采购单，用于申报单订单联动。 */
    @Transactional(readOnly = true)
    public ProcurementApi.PurchaseOrderLinkView findOrderBySerialNo(String serialNo) {
        if (serialNo == null || serialNo.isBlank()) {
            return null;
        }
        String normalized = serialNo.trim().toUpperCase();
        for (PurchaseReceiptLine receiptLine : receiptLineRepository.searchBySerialNo(normalized)) {
            if (!parseSerialNos(receiptLine.getSerialNos()).contains(normalized)) {
                continue;
            }
            if (receiptLine.getOrderLineId() == null) {
                continue;
            }
            PurchaseOrderLine orderLine = lineRepository.findById(receiptLine.getOrderLineId()).orElse(null);
            if (orderLine == null) {
                continue;
            }
            PurchaseOrder order = orderRepository.findById(orderLine.getOrderId()).orElse(null);
            if (order != null) {
                return new ProcurementApi.PurchaseOrderLinkView(order.getId(), order.getOrderNo(),
                        order.getExternalOrderNo(), receiptLine.getUnitPrice());
            }
        }
        return null;
    }

    /** 收货完成后可补录或修正申请合格证日期，并同步对应 VIN 档案。 */
    @Transactional
    public ProcurementApi.PurchaseReceiptView updateCertificateApplicationDate(
            UUID receiptId, UUID lineId, LocalDate certificateApplicationDate) {
        PurchaseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("收货单", receiptId));
        PurchaseReceiptLine line = receiptLineRepository.findByIdAndReceiptId(lineId, receiptId)
                .orElseThrow(() -> new NotFoundException("收货明细", lineId));
        if (isBattery(masterData.requireProduct(line.getProductId()))) {
            throw new ConflictException("BATTERY_WAREHOUSE_FIXED", "动力电池统一收货仓库为天津容大，不能单独修改");
        }
        line.updateCertificateApplicationDate(certificateApplicationDate);
        receiptLineRepository.save(line);
        inventoryReceiptPort.updateCertificateApplicationDate(parseSerialNos(line.getSerialNos()),
                certificateApplicationDate);
        return toReceiptView(receipt);
    }

    /** 单个 VIN 收货后临时调整仓库，并同步库存台账与序列号档案。 */
    @Transactional
    public ProcurementApi.PurchaseReceiptView updateReceiptWarehouse(UUID receiptId, UUID lineId, UUID warehouseId) {
        PurchaseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("收货单", receiptId));
        PurchaseReceiptLine line = receiptLineRepository.findByIdAndReceiptId(lineId, receiptId)
                .orElseThrow(() -> new NotFoundException("收货明细", lineId));
        if (warehouseId == null) {
            throw new ConflictException("RECEIVE_WAREHOUSE_REQUIRED", "请选择收货仓库");
        }
        masterData.requireWarehouse(warehouseId);
        List<String> serialNos = parseSerialNos(line.getSerialNos());
        if (serialNos.size() != 1) {
            throw new ConflictException("VIN_WAREHOUSE_CHANGE_INVALID", "只能按单个 VIN/SN 调整收货仓库");
        }
        UUID fromWarehouseId = line.getWarehouseId() != null ? line.getWarehouseId() : receipt.getWarehouseId();
        if (!warehouseId.equals(fromWarehouseId)) {
            inventoryReceiptPort.relocateSerial(serialNos.get(0), line.getProductId(), fromWarehouseId, warehouseId,
                    receipt.getId(), receipt.getReceiptNo());
            line.updateWarehouse(warehouseId);
            receiptLineRepository.save(line);
        }
        return toReceiptView(receipt);
    }

    private record Prepared(PurchaseOrderLine orderLine, MasterDataQueryPort.ProductInfo product,
                            UUID warehouseId, BigDecimal quantity, BigDecimal unitPrice, String batchNo,
                            List<String> serialNos, LocalDate certificateApplicationDate) {
    }

    private record BatteryPricing(BigDecimal unitPrice, String boxManufacturer, String boxModel,
                                  BigDecimal boxUnitPrice, String packManufacturer, BigDecimal energyKwh,
                                  BigDecimal packUnitPrice) {
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
            normalized.add(value);
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
            UUID lineWarehouseId = line.getWarehouseId() != null ? line.getWarehouseId() : order.getWarehouseId();
            MasterDataQueryPort.WarehouseInfo lineWarehouse = lineWarehouseId == null ? null
                    : masterData.requireWarehouse(lineWarehouseId);
            List<ProcurementApi.PolicyRefView> policies = new ArrayList<>();
            for (UUID policyId : policyIdsByLine.getOrDefault(line.getId(), List.of())) {
                PurchasePolicyPort.PolicyInfo info = policyById.get(policyId);
                if (info != null) {
                    policies.add(new ProcurementApi.PolicyRefView(info.id(), info.policyNo(), info.name()));
                }
            }
            lines.add(new ProcurementApi.PurchaseOrderLineView(line.getId(), line.getLineNo(),
                    line.getProductId(), product.productNo(), product.name(), product.series(), product.configCode(),
                    line.getConfigText() != null ? line.getConfigText() : product.configCode(),
                    line.getSerialNo(), cabType(line.getConfigText(), product.name()),
                    line.getCertificateApplicationDate(), line.getArrivalDate(),
                    lineWarehouseId, lineWarehouse == null ? null : lineWarehouse.name(),
                    product.categoryCode(), product.categoryName(), product.trackingMode(), line.getQuantity(),
                    line.getReceivedQuantity(), line.remaining(), line.getUnitPrice(), line.getBatteryBoxUnitPrice(),
                    line.getBatteryBoxManufacturer(), line.getBatteryBoxModel(), line.getBatteryPackManufacturer(),
                    line.getBatteryEnergyKwh(), line.getBatteryPackUnitPrice(), line.getTaxRate(), line.getAmount(),
                    line.getRemark(), purchaseLineStatus(line), policies));
        }
        return ProcurementApi.PurchaseOrderView.from(order,
                new ProcurementApi.SupplierInfoView(supplier.id(), supplier.name(), supplier.supplierType()),
                warehouse == null ? null : new ProcurementApi.WarehouseInfoView(warehouse.id(), warehouse.name()),
                purchaseScope(lineEntities), lines);
    }

    private ProcurementApi.PurchaseReceiptView toReceiptView(PurchaseReceipt receipt) {
        String orderNo = orderRepository.findById(receipt.getOrderId())
                .map(order -> order.getExternalOrderNo() != null ? order.getExternalOrderNo() : order.getOrderNo())
                .orElse(null);
        String supplierName = masterData.requireSupplier(receipt.getSupplierId()).name();
        String warehouseName = receipt.getWarehouseId() == null ? null
                : masterData.requireWarehouse(receipt.getWarehouseId()).name();
        List<ProcurementApi.PurchaseReceiptLineView> lines = receiptLineRepository.findByReceiptId(receipt.getId())
                .stream().map(line -> {
                    MasterDataQueryPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                    PurchaseOrderLine orderLine = line.getOrderLineId() == null ? null
                            : lineRepository.findById(line.getOrderLineId()).orElse(null);
                    String configText = orderLine == null ? null : orderLine.getConfigText();
                    UUID lineWarehouseId = line.getWarehouseId() != null ? line.getWarehouseId()
                            : receipt.getWarehouseId();
                    String lineWarehouseName = lineWarehouseId == null ? null
                            : masterData.requireWarehouse(lineWarehouseId).name();
                    return new ProcurementApi.PurchaseReceiptLineView(line.getId(), line.getProductId(),
                            product.productNo(), product.name(), product.series(), configText,
                            lineWarehouseId, lineWarehouseName, cabType(configText, product.name()),
                            line.getQuantity(), line.getUnitPrice(), line.getBatchNo(),
                            parseSerialNos(line.getSerialNos()), line.getCertificateApplicationDate(),
                            orderLine == null ? null : orderLine.getBatteryBoxManufacturer(),
                            orderLine == null ? null : orderLine.getBatteryBoxModel(),
                            orderLine == null ? null : orderLine.getBatteryBoxUnitPrice(),
                            orderLine == null ? null : orderLine.getBatteryPackManufacturer(),
                            orderLine == null ? null : orderLine.getBatteryEnergyKwh(),
                            orderLine == null ? null : orderLine.getBatteryPackUnitPrice());
                }).toList();
        String businessScope = orderRepository.findById(receipt.getOrderId())
                .map(this::purchaseScope).orElse("VEHICLE");
        return ProcurementApi.PurchaseReceiptView.from(receipt, orderNo, supplierName, warehouseName,
                businessScope, lines);
    }

    private String cabType(String configText, String productName) {
        String source = configText == null || configText.isBlank() ? productName : configText;
        if (source == null || source.isBlank()) {
            return null;
        }
        int marker = Math.max(source.indexOf("标载"), Math.max(source.indexOf("复合"), source.indexOf("重载")));
        String suffix = marker >= 0 ? source.substring(marker + 2) : source;
        if (suffix.contains("财富版")) {
            return "平顶";
        }
        if (suffix.contains("舒享版")) {
            return "高顶";
        }
        return null;
    }

    private BatteryPricing batteryPricing(ProcurementApi.SavePurchaseOrderLineCommand line, boolean battery) {
        if (!battery) {
            return new BatteryPricing(line.unitPrice(), null, null, null, null, null, null);
        }
        String boxManufacturer = requiredBatteryText(line.batteryBoxManufacturer(), "电池箱制造商");
        String boxModel = requiredBatteryText(line.batteryBoxModel(), "电池箱型号");
        String packManufacturer = requiredBatteryText(line.batteryPackManufacturer(), "电池包制造商");
        if (line.arrivalDate() == null) {
            throw new ConflictException("BATTERY_ARRIVAL_REQUIRED", "动力电池请填写到货时间");
        }
        BigDecimal boxPrice = requiredBatteryMoney(line.batteryBoxUnitPrice(), "电池箱单价");
        BigDecimal packPrice = requiredBatteryMoney(line.batteryPackUnitPrice(), "电池包单价");
        if (line.batteryEnergyKwh() == null || line.batteryEnergyKwh().signum() <= 0) {
            throw new ConflictException("BATTERY_ENERGY_REQUIRED", "动力电池能量（kWh）必须大于 0");
        }
        BigDecimal combinedUnitPrice = boxPrice.add(packPrice);
        if (combinedUnitPrice.signum() <= 0) {
            throw new ConflictException("BATTERY_PRICE_REQUIRED", "电池箱单价与电池包单价合计必须大于 0");
        }
        return new BatteryPricing(combinedUnitPrice, boxManufacturer, boxModel, boxPrice, packManufacturer,
                line.batteryEnergyKwh().setScale(3, RoundingMode.HALF_UP), packPrice);
    }

    private String requiredBatteryText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConflictException("BATTERY_DETAIL_REQUIRED", "动力电池请填写" + field);
        }
        return value.trim();
    }

    private BigDecimal requiredBatteryMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new ConflictException("BATTERY_PRICE_REQUIRED", "动力电池请填写有效的" + field);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBattery(MasterDataQueryPort.ProductInfo product) {
        String code = product.categoryCode() == null ? "" : product.categoryCode().toUpperCase();
        String name = product.categoryName() == null ? "" : product.categoryName();
        return code.equals("BATTERY") || code.startsWith("BATTERY-") || name.contains("电池");
    }

    private String purchaseScope(List<PurchaseOrderLine> lines) {
        for (PurchaseOrderLine line : lines) {
            if (isBattery(masterData.requireProduct(line.getProductId()))) {
                return "BATTERY";
            }
        }
        return "VEHICLE";
    }

    private String purchaseScope(PurchaseOrder order) {
        return purchaseScope(lineRepository.findByOrderIdOrderByLineNoAsc(order.getId()));
    }

    private String purchaseLineStatus(PurchaseOrderLine line) {
        if (line.getReceivedQuantity().signum() == 0) {
            return "待收货";
        }
        if (line.fullyReceived()) {
            return "已收货";
        }
        return "部分收货";
    }
}
