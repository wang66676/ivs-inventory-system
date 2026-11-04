package com.ivs.sales.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.sales.settlement.Receivable;
import com.ivs.sales.settlement.ReceivableRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 销售订单与出库。
 *
 * <p>主链路：建单（自动取价 + 低价标记）→ 价格审批（如需要）→ 确认（信用额度校验）→ 锁货 →
 * 出库（扣库存、结转成本、生成应收）→ 收款核销 → 开票。</p>
 */
@Service
public class SalesOrderApplicationService {

    private static final int DEFAULT_PAYMENT_TERM_DAYS = 30;

    private final SalesOrderRepository orderRepository;
    private final SalesOrderLineRepository lineRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final SalesDeliveryLineRepository deliveryLineRepository;
    private final ReceivableRepository receivableRepository;
    private final SalesMasterDataPort masterData;
    private final SalesStockPort stockPort;
    private final EventPublisher events;
    private final CurrentActorProvider actors;
    private final ObjectMapper objectMapper;

    public SalesOrderApplicationService(SalesOrderRepository orderRepository,
                                        SalesOrderLineRepository lineRepository,
                                        SalesDeliveryRepository deliveryRepository,
                                        SalesDeliveryLineRepository deliveryLineRepository,
                                        ReceivableRepository receivableRepository,
                                        SalesMasterDataPort masterData,
                                        SalesStockPort stockPort,
                                        EventPublisher events,
                                        CurrentActorProvider actors,
                                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryLineRepository = deliveryLineRepository;
        this.receivableRepository = receivableRepository;
        this.masterData = masterData;
        this.stockPort = stockPort;
        this.events = events;
        this.actors = actors;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SalesApi.SalesOrderView> list(String keyword, SalesOrderStatus status, UUID customerId) {
        List<SalesOrder> orders;
        if (keyword != null && !keyword.isBlank()) {
            orders = orderRepository.searchByOrderNo("%" + keyword.trim().toLowerCase() + "%");
        } else if (customerId != null) {
            orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        } else if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orders.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SalesApi.SalesOrderView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public SalesApi.SalesOrderView create(SalesApi.SaveSalesOrderCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("SALES_LINE_REQUIRED", "销售订单至少需要一行明细");
        }
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(command.customerId());
        if (command.warehouseId() != null) {
            masterData.requireWarehouse(command.warehouseId());
        }
        String orderNo = "SO-%06d".formatted(orderRepository.nextOrderSequence());
        SalesOrder order = orderRepository.save(SalesOrder.create(orderNo, command.externalOrderNo(), customer.id(),
                command.warehouseId(), BigDecimal.ZERO, false, command.remark(), currentUserId()));
        PreparedOrder prepared = prepareLines(order, customer, command.lines());
        order.markPriceApprovalRequired(prepared.approvalRequired());
        order.updateTotal(prepared.total());
        orderRepository.save(order);
        publish(order, "SalesOrderCreated", Map.of("orderNo", orderNo, "totalAmount", prepared.total()));
        return toView(order);
    }

    /** 草稿订单补全 VIN、客户、仓库或备注；一旦确认锁货后不可再修改。 */
    @Transactional
    public SalesApi.SalesOrderView update(UUID id, SalesApi.SaveSalesOrderCommand command) {
        SalesOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("销售订单", id));
        if (order.getStatus() != SalesOrderStatus.DRAFT) {
            throw new ConflictException("SALES_STATUS_INVALID", "只有草稿订单可以编辑: " + order.getStatus());
        }
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(command.customerId());
        if (command.warehouseId() != null) {
            masterData.requireWarehouse(command.warehouseId());
        }
        try {
            order.updateDraft(command.externalOrderNo(), customer.id(), command.warehouseId(), command.remark());
        } catch (IllegalStateException exception) {
            throw new ConflictException("SALES_STATUS_INVALID", exception.getMessage());
        }
        List<SalesOrderLine> existing = lineRepository.findByOrderIdOrderByLineNoAsc(order.getId());
        lineRepository.deleteAll(existing);
        lineRepository.flush();

        PreparedOrder prepared = prepareLines(order, customer, command.lines());
        order.resetPriceApproval();
        order.markPriceApprovalRequired(prepared.approvalRequired());
        order.updateTotal(prepared.total());
        SalesOrder saved = orderRepository.saveAndFlush(order);
        publish(saved, "SalesOrderUpdated", Map.of("orderNo", saved.getOrderNo(), "totalAmount", prepared.total()));
        return toView(saved);
    }

    private PreparedOrder prepareLines(SalesOrder order, SalesMasterDataPort.CustomerInfo customer,
                                       List<SalesApi.SaveSalesOrderLineCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new ConflictException("SALES_LINE_REQUIRED", "销售订单至少需要一行明细");
        }
        int lineNo = 1;
        BigDecimal total = BigDecimal.ZERO;
        boolean approvalRequired = false;
        for (SalesApi.SaveSalesOrderLineCommand line : commands) {
            SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.productId());
            SalesMasterDataPort.PriceInfo reference = masterData.lookupPrice(product.id(),
                    customer.customerType(), LocalDate.now());
            boolean battery = isBattery(product);
            BigDecimal batteryBoxPrice = null;
            BigDecimal batteryPackPrice = null;
            BigDecimal effectiveUnitPrice;
            if (battery) {
                batteryBoxPrice = requiredBatteryMoney(line.batteryBoxUnitPrice(), "电池箱");
                batteryPackPrice = requiredBatteryMoney(line.batteryPackUnitPrice(), "电池包");
                effectiveUnitPrice = batteryBoxPrice.add(batteryPackPrice);
                if (effectiveUnitPrice.signum() <= 0) {
                    throw new ConflictException("SALES_PRICE_REQUIRED", "电池箱与电池包成交价合计必须大于 0");
                }
            } else {
                effectiveUnitPrice = line.unitPrice() == null ? reference.price() : line.unitPrice();
            }
            if (effectiveUnitPrice == null || effectiveUnitPrice.signum() <= 0) {
                throw new ConflictException("SALES_PRICE_REQUIRED",
                        "商品「" + product.name() + "」成交价必须大于 0，请填写成交价后再保存订单");
            }
            String boxManufacturer = null;
            String boxModel = null;
            String packManufacturer = null;
            BigDecimal energyKwh = null;
            if (battery) {
                boxManufacturer = requiredBatteryText(line.batteryBoxManufacturer(), "电池箱制造商");
                boxModel = requiredBatteryText(line.batteryBoxModel(), "电池箱型号");
                packManufacturer = requiredBatteryText(line.batteryPackManufacturer(), "电池包制造商");
                if (line.batteryEnergyKwh() == null || line.batteryEnergyKwh().signum() <= 0) {
                    throw new ConflictException("BATTERY_ENERGY_REQUIRED", "动力电池能量（kWh）必须大于 0");
                }
                energyKwh = line.batteryEnergyKwh().setScale(3, RoundingMode.HALF_UP);
            }
            List<UUID> serialIds = normalizeIds(line.serialIds());
            BigDecimal quantity = "SERIAL".equals(product.trackingMode())
                    ? (serialIds.isEmpty() ? line.quantity() : BigDecimal.valueOf(serialIds.size()))
                    : line.quantity();
            SalesOrderLine entity;
            try {
                entity = SalesOrderLine.create(order.getId(), lineNo++, product.id(), quantity, effectiveUnitPrice,
                        reference.minPrice(), null, serialIdsJson(serialIds), line.remark(), boxManufacturer,
                        boxModel, batteryBoxPrice, packManufacturer, energyKwh, batteryPackPrice);
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("SALES_LINE_INVALID", exception.getMessage());
            }
            if (entity.belowMinPrice()) {
                approvalRequired = true;
            }
            lineRepository.save(entity);
            total = total.add(entity.getAmount());
        }
        return new PreparedOrder(total, approvalRequired);
    }

    /** 已确认订单在出库前补录成交价，不改变商品、数量和已锁定的 VIN。 */
    @Transactional
    public SalesApi.SalesOrderView updatePrices(UUID id, SalesApi.UpdatePricesCommand command) {
        SalesOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("销售订单", id));
        if (order.getStatus() != SalesOrderStatus.DRAFT && order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new ConflictException("SALES_STATUS_INVALID", "当前订单状态不允许修改成交价: " + order.getStatus());
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("SALES_PRICE_REQUIRED", "请填写成交价");
        }
        List<SalesOrderLine> orderLines = lineRepository.findByOrderIdOrderByLineNoAsc(id);
        if (orderLines.stream().anyMatch(line -> line.getDeliveredQuantity().signum() > 0)) {
            throw new ConflictException("SALES_STATUS_INVALID", "已有出库的订单不能修改成交价");
        }
        Map<UUID, SalesApi.UpdatePriceLineCommand> prices = new LinkedHashMap<>();
        for (SalesApi.UpdatePriceLineCommand priceLine : command.lines()) {
            if (priceLine.orderLineId() != null && priceLine.unitPrice() != null) {
                prices.put(priceLine.orderLineId(), priceLine);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : orderLines) {
            SalesApi.UpdatePriceLineCommand priceLine = prices.get(line.getId());
            BigDecimal price = priceLine == null ? null : priceLine.unitPrice();
            if (price == null || price.signum() <= 0) {
                SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                throw new ConflictException("SALES_PRICE_REQUIRED",
                        "商品「" + product.name() + "」成交价必须大于 0");
            }
            try {
                SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                if (isBattery(product)) {
                    BigDecimal boxPrice = requiredBatteryMoney(priceLine.batteryBoxUnitPrice(), "电池箱");
                    BigDecimal packPrice = requiredBatteryMoney(priceLine.batteryPackUnitPrice(), "电池包");
                    line.updateUnitPrice(boxPrice.add(packPrice), boxPrice, packPrice);
                } else {
                    line.updateUnitPrice(price);
                }
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("SALES_PRICE_REQUIRED", exception.getMessage());
            }
            if (line.belowMinPrice()) {
                throw new ConflictException("SALES_PRICE_BELOW_MIN", "成交价低于最低限价，请通过草稿编辑并完成价格审批");
            }
            lineRepository.save(line);
            total = total.add(line.getAmount());
        }
        order.resetPriceApproval();
        order.markPriceApprovalRequired(false);
        order.updateTotal(total);
        SalesOrder saved = orderRepository.saveAndFlush(order);
        publish(saved, "SalesOrderPricesUpdated", Map.of("orderNo", saved.getOrderNo(), "totalAmount", total));
        return toView(saved);
    }

    /** 价格审批：存在低于最低限价的明细时必须先审批才能确认订单。 */
    @Transactional
    public SalesApi.SalesOrderView approvePrice(UUID id) {
        SalesOrder order = find(id);
        if (!order.isPriceApprovalRequired()) {
            throw new ConflictException("PRICE_APPROVAL_NOT_REQUIRED", "该订单不存在低于最低限价的明细");
        }
        order.approvePrice(currentUserId());
        orderRepository.save(order);
        publish(order, "SalesOrderPriceApproved", Map.of("orderNo", order.getOrderNo()));
        return toView(order);
    }

    @Transactional
    public SalesApi.SalesOrderView confirm(UUID id) {
        SalesOrder order = find(id);
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(order.getCustomerId());
        if (customer.creditLimit() != null && customer.creditLimit().signum() > 0
                && order.getTotalAmount().compareTo(customer.creditLimit()) > 0) {
            throw new ConflictException("CREDIT_LIMIT_EXCEEDED", "订单金额超过客户信用额度："
                    + customer.creditLimit().toPlainString());
        }
        try {
            order.confirm();
        } catch (IllegalStateException exception) {
            throw new ConflictException("SALES_STATUS_INVALID", exception.getMessage());
        }
        return toView(orderRepository.saveAndFlush(order));
    }

    /** 锁货：序列号商品按序列号逐台锁定，数量商品按数量锁定。 */
    @Transactional
    public SalesApi.SalesOrderView lockOrder(UUID id, UUID warehouseOverride) {
        SalesOrder order = find(id);
        if (order.getStatus() != SalesOrderStatus.CONFIRMED && order.getStatus() != SalesOrderStatus.PARTIALLY_DELIVERED) {
            throw new ConflictException("SALES_STATUS_INVALID", "只有已确认的订单可以锁货");
        }
        if (order.isLocked()) {
            return toView(order);
        }
        UUID warehouseId = resolveWarehouseId(order, warehouseOverride);
        List<SalesOrderLine> lines = lineRepository.findByOrderIdOrderByLineNoAsc(order.getId());
        List<SalesStockPort.LineLock> locks = new ArrayList<>();
        for (SalesOrderLine line : lines) {
            if (line.fullyDelivered()) {
                continue;
            }
            SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.getProductId());
            List<UUID> serialIds = parseIds(line.getSerialIds());
            if ("SERIAL".equals(product.trackingMode())) {
                BigDecimal selected = BigDecimal.valueOf(serialIds.size());
                if (serialIds.isEmpty() || selected.compareTo(line.remaining()) != 0) {
                    throw new ConflictException("SALES_VIN_REQUIRED",
                            "商品「" + product.name() + "」需先补全并选定全部 VIN 后才能锁货");
                }
            }
            locks.add(new SalesStockPort.LineLock(line.getId(), line.getProductId(), serialIds, line.remaining()));
        }
        stockPort.allocate(order.getId(), order.getOrderNo(), warehouseId, locks);
        order.markLocked();
        SalesOrder saved = orderRepository.saveAndFlush(order);
        publish(saved, "SalesOrderLocked", Map.of("orderNo", saved.getOrderNo(), "lineCount", locks.size()));
        return toView(saved);
    }

    @Transactional
    public SalesApi.SalesOrderView cancel(UUID id) {
        SalesOrder order = find(id);
        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("SALES_STATUS_INVALID", exception.getMessage());
        }
        stockPort.releaseAll(order.getId());
        return toView(orderRepository.saveAndFlush(order));
    }

    /** 已取消订单可直接删除；业务上取消前不允许存在出库记录。 */
    @Transactional
    public void deleteCancelled(UUID id) {
        SalesOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("销售订单", id));
        if (order.getStatus() != SalesOrderStatus.CANCELLED) {
            throw new ConflictException("SALES_STATUS_INVALID", "只有已取消订单可以删除");
        }
        lineRepository.deleteAll(lineRepository.findByOrderIdOrderByLineNoAsc(id));
        orderRepository.delete(order);
    }

    /** 出库：扣减库存、结转成本、生成应收，并处理锁定。 */
    @Transactional
    public SalesApi.SalesDeliveryView deliver(UUID orderId, SalesApi.DeliverCommand command) {
        SalesOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("销售订单", orderId));
        if (!order.deliverable()) {
            throw new ConflictException("SALES_STATUS_INVALID", "当前订单状态不允许出库: " + order.getStatus());
        }
        if (!order.isLocked()) {
            throw new ConflictException("SALES_NOT_LOCKED", "请先选定 VIN 并完成锁货，锁货后才能出库");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("DELIVERY_LINE_REQUIRED", "请至少填写一行出库明细");
        }
        UUID warehouseId = resolveWarehouseId(order, command.warehouseId());
        masterData.requireWarehouse(warehouseId);

        List<SalesOrderLine> orderLines = lineRepository.findByOrderIdOrderByLineNoAsc(orderId);
        Map<UUID, SalesOrderLine> byId = new LinkedHashMap<>();
        orderLines.forEach(line -> byId.put(line.getId(), line));

        // 第一遍：校验
        List<PreparedDelivery> prepared = new ArrayList<>();
        for (SalesApi.DeliverLineCommand deliverLine : command.lines()) {
            SalesOrderLine orderLine = byId.get(deliverLine.orderLineId());
            if (orderLine == null) {
                throw new NotFoundException("销售明细", deliverLine.orderLineId());
            }
            SalesMasterDataPort.ProductInfo product = masterData.requireProduct(orderLine.getProductId());
            if (orderLine.getUnitPrice() == null || orderLine.getUnitPrice().signum() <= 0) {
                throw new ConflictException("SALES_PRICE_REQUIRED",
                        "商品「" + product.name() + "」成交价为 0，请先补录成交价再出库");
            }
            List<UUID> serialIds = normalizeIds(deliverLine.serialIds());
            BigDecimal quantity;
            if ("SERIAL".equals(product.trackingMode())) {
                if (serialIds.isEmpty()) {
                    throw new ConflictException("DELIVERY_SERIAL_REQUIRED",
                            "商品「" + product.name() + "」按序列号管理，请选择本次交付的 VIN / 电池 SN");
                }
                List<UUID> lockedSerialIds = parseIds(orderLine.getSerialIds());
                if (!lockedSerialIds.containsAll(serialIds)) {
                    throw new ConflictException("DELIVERY_SERIAL_NOT_IN_ORDER",
                            "商品「" + product.name() + "」本次选择的 VIN 不属于该订单已锁定范围");
                }
                quantity = BigDecimal.valueOf(serialIds.size());
            } else {
                quantity = deliverLine.quantity() == null ? BigDecimal.ZERO : deliverLine.quantity();
            }
            if (quantity.signum() <= 0) {
                throw new ConflictException("DELIVERY_QUANTITY_REQUIRED", "出库数量必须大于 0");
            }
            if (quantity.compareTo(orderLine.remaining()) > 0) {
                throw new ConflictException("DELIVERY_QUANTITY_EXCEEDED", "商品「" + product.name()
                        + "」出库数量超过剩余可出：" + orderLine.remaining().toPlainString());
            }
            stockPort.requireDeliverable(product.id(), warehouseId, quantity);
            prepared.add(new PreparedDelivery(orderLine, product, quantity, serialIds));
        }

        // 第二遍：出库（写台账、结转成本）
        String orderNo = order.getOrderNo();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<Object[]> issued = new ArrayList<>();
        for (PreparedDelivery item : prepared) {
            BigDecimal cost = stockPort.issue(order.getId(), orderNo, warehouseId, item.orderLine().getId(),
                    item.product().id(), item.serialIds(), item.quantity());
            totalAmount = totalAmount.add(item.quantity().multiply(item.orderLine().getUnitPrice()));
            totalCost = totalCost.add(cost);
            issued.add(new Object[]{item, cost});
        }

        String deliveryNo = "DL-%06d".formatted(deliveryRepository.nextDeliverySequence());
        SalesDelivery delivery = deliveryRepository.save(SalesDelivery.create(deliveryNo, order.getId(),
                order.getCustomerId(), warehouseId, totalAmount, totalCost, command.remark()));
        for (Object[] row : issued) {
            PreparedDelivery item = (PreparedDelivery) row[0];
            BigDecimal cost = (BigDecimal) row[1];
            deliveryLineRepository.save(SalesDeliveryLine.create(delivery.getId(), item.orderLine().getId(),
                    item.product().id(), item.quantity(), item.orderLine().getUnitPrice(), cost,
                    serialIdsJson(item.serialIds())));
            item.orderLine().deliver(item.quantity());
            lineRepository.save(item.orderLine());
            List<UUID> remainingSerialIds = new ArrayList<>(parseIds(item.orderLine().getSerialIds()));
            remainingSerialIds.removeAll(item.serialIds());
            stockPort.settleLocks(order.getId(), item.orderLine().getId(), item.product().id(), warehouseId,
                    item.serialIds(), remainingSerialIds, item.orderLine().remaining());
        }

        // 生成应收（到期日 = 今天 + 客户账期天数，默认 30 天）
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(order.getCustomerId());
        int termDays = customer.paymentTermDays() > 0 ? customer.paymentTermDays() : DEFAULT_PAYMENT_TERM_DAYS;
        LocalDate dueDate = delivery.getDeliveredAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .plusDays(termDays);
        Receivable receivable = Receivable.create("AR-%06d".formatted(receivableRepository.nextReceivableSequence()),
                order.getCustomerId(), order.getId(), delivery.getId(), totalAmount, dueDate, "出库 " + deliveryNo);
        receivableRepository.save(receivable);

        boolean fullyDelivered = orderLines.stream().allMatch(SalesOrderLine::fullyDelivered);
        order.refreshDeliveryStatus(fullyDelivered);
        if (fullyDelivered) {
            stockPort.releaseAll(order.getId());
        }
        orderRepository.save(order);

        publish(order, "SalesOrderDelivered", Map.of(
                "orderNo", orderNo, "deliveryNo", deliveryNo, "amount", totalAmount, "cost", totalCost,
                "grossProfit", totalAmount.subtract(totalCost)));
        return toDeliveryView(delivery);
    }

    /** 款货两清后关闭订单（由收款核销流程调用）。 */
    @Transactional
    public void closeIfSettled(UUID orderId) {
        SalesOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != SalesOrderStatus.DELIVERED) {
            return;
        }
        order.close();
        orderRepository.save(order);
        publish(order, "SalesOrderClosed", Map.of("orderNo", order.getOrderNo()));
    }

    @Transactional(readOnly = true)
    public List<SalesApi.SalesDeliveryView> listDeliveries() {
        return deliveryRepository.findAllByOrderByDeliveredAtDesc().stream().map(this::toDeliveryView).toList();
    }

    @Transactional(readOnly = true)
    public SalesApi.SalesDeliveryView getDelivery(UUID id) {
        return deliveryRepository.findById(id).map(this::toDeliveryView)
                .orElseThrow(() -> new NotFoundException("出库单", id));
    }


    private record PreparedOrder(BigDecimal total, boolean approvalRequired) {
    }

    private record PreparedDelivery(SalesOrderLine orderLine, SalesMasterDataPort.ProductInfo product,
                                    BigDecimal quantity, List<UUID> serialIds) {
    }

    private UUID resolveWarehouseId(SalesOrder order, UUID override) {
        UUID warehouseId = override != null ? override : order.getWarehouseId();
        if (warehouseId == null) {
            throw new ConflictException("SALES_WAREHOUSE_REQUIRED", "请指定出库仓库");
        }
        return warehouseId;
    }

    private List<UUID> normalizeIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        List<UUID> normalized = new ArrayList<>();
        for (UUID id : ids) {
            if (id != null && !normalized.contains(id)) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    private String serialIdsJson(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids.stream().map(UUID::toString).toList());
        } catch (Exception exception) {
            return null;
        }
    }

    private List<UUID> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            return raw.stream().map(UUID::fromString).toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void publish(SalesOrder order, String eventType, Map<String, Object> payload) {
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "SalesOrder", order.getId(),
                order.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }

    private SalesOrder find(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("销售订单", id));
    }

    private SalesApi.SalesOrderView toView(SalesOrder order) {
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(order.getCustomerId());
        String warehouseName = order.getWarehouseId() == null ? null
                : masterData.requireWarehouse(order.getWarehouseId()).name();
        List<SalesApi.SalesOrderLineView> lines = lineRepository.findByOrderIdOrderByLineNoAsc(order.getId()).stream()
                .map(line -> {
                    SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                    List<UUID> serialIds = parseIds(line.getSerialIds());
                    List<UUID> deliveredSerialIds = deliveryLineRepository.findByOrderLineId(line.getId()).stream()
                            .flatMap(deliveryLine -> parseIds(deliveryLine.getSerialIds()).stream()).toList();
                    List<UUID> remainingSerialIds = new ArrayList<>(serialIds);
                    remainingSerialIds.removeAll(deliveredSerialIds);
                    return new SalesApi.SalesOrderLineView(line.getId(), line.getLineNo(), line.getProductId(),
                            product.productNo(), product.name(), product.categoryCode(), product.categoryName(),
                            product.trackingMode(), line.getQuantity(), line.getDeliveredQuantity(), line.remaining(),
                            line.getUnitPrice(), line.getMinPrice(), line.getTaxRate(), line.getAmount(),
                            line.getBatteryBoxManufacturer(), line.getBatteryBoxModel(),
                            line.getBatteryBoxUnitPrice(), line.getBatteryPackManufacturer(),
                            line.getBatteryEnergyKwh(), line.getBatteryPackUnitPrice(), line.belowMinPrice(),
                            serialIds, remainingSerialIds, line.getRemark());
                })
                .toList();
        return SalesApi.SalesOrderView.from(order, customer.name(), customer.customerType(), warehouseName, lines);
    }

    private SalesApi.SalesDeliveryView toDeliveryView(SalesDelivery delivery) {
        String orderNo = orderRepository.findById(delivery.getOrderId()).map(SalesOrder::getOrderNo).orElse(null);
        String customerName = masterData.requireCustomer(delivery.getCustomerId()).name();
        String warehouseName = masterData.requireWarehouse(delivery.getWarehouseId()).name();
        List<SalesApi.SalesDeliveryLineView> lines = deliveryLineRepository.findByDeliveryId(delivery.getId())
                .stream().map(line -> {
                    SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                    return new SalesApi.SalesDeliveryLineView(line.getId(), line.getProductId(), product.productNo(),
                            product.name(), line.getQuantity(), line.getUnitPrice(), line.getCostAmount(),
                            parseIds(line.getSerialIds()));
                }).toList();
        return SalesApi.SalesDeliveryView.from(delivery, orderNo, customerName, warehouseName, lines);
    }

    private boolean isBattery(SalesMasterDataPort.ProductInfo product) {
        String code = product.categoryCode() == null ? "" : product.categoryCode().toUpperCase();
        String name = product.categoryName() == null ? "" : product.categoryName();
        return code.equals("BATTERY") || code.startsWith("BATTERY-") || name.contains("电池");
    }

    private String requiredBatteryText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConflictException("BATTERY_DETAIL_REQUIRED", "动力电池请填写" + field);
        }
        return value.trim();
    }

    private BigDecimal requiredBatteryMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new ConflictException("BATTERY_PRICE_REQUIRED", "动力电池请填写有效的" + field + "成交价");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
