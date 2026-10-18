package com.ivs.sales.returns;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesDeliveryLine;
import com.ivs.sales.order.SalesDeliveryLineRepository;
import com.ivs.sales.order.SalesDeliveryRepository;
import com.ivs.sales.order.SalesMasterDataPort;
import com.ivs.sales.returns.api.SalesReturnApi;
import com.ivs.sales.settlement.Receivable;
import com.ivs.sales.settlement.ReceivableRepository;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 销售退货：回库、恢复可售、冲减应收与多收退款登记。 */
@Service
public class SalesReturnApplicationService {

    private final SalesReturnRepository returnRepository;
    private final SalesReturnLineRepository lineRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final SalesDeliveryLineRepository deliveryLineRepository;
    private final ReceivableRepository receivableRepository;
    private final SalesMasterDataPort masterData;
    private final SalesReturnStockPort stockPort;
    private final CurrentActorProvider actors;
    private final ObjectMapper objectMapper;

    public SalesReturnApplicationService(SalesReturnRepository returnRepository,
                                         SalesReturnLineRepository lineRepository,
                                         SalesDeliveryRepository deliveryRepository,
                                         SalesDeliveryLineRepository deliveryLineRepository,
                                         ReceivableRepository receivableRepository,
                                         SalesMasterDataPort masterData,
                                         SalesReturnStockPort stockPort,
                                         CurrentActorProvider actors,
                                         ObjectMapper objectMapper) {
        this.returnRepository = returnRepository;
        this.lineRepository = lineRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryLineRepository = deliveryLineRepository;
        this.receivableRepository = receivableRepository;
        this.masterData = masterData;
        this.stockPort = stockPort;
        this.actors = actors;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SalesReturnApi.ReturnView> list(UUID customerId, SalesReturnStatus status) {
        List<SalesReturn> rows = customerId != null
                ? returnRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                : status != null ? returnRepository.findByStatusOrderByCreatedAtDesc(status)
                : returnRepository.findAllByOrderByCreatedAtDesc();
        return rows.stream().filter(row -> status == null || row.getStatus() == status)
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SalesReturnApi.ReturnView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public SalesReturnApi.ReturnView create(SalesReturnApi.CreateReturnCommand command) {
        if (command == null || command.customerId() == null || command.warehouseId() == null) {
            throw new ConflictException("SALES_RETURN_REQUIRED", "请选择客户与退货仓库");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ConflictException("SALES_RETURN_LINE_REQUIRED", "销售退货至少需要一条明细");
        }
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(command.customerId());
        masterData.requireWarehouse(command.warehouseId());
        SalesDelivery delivery = null;
        if (command.deliveryId() != null) {
            delivery = deliveryRepository.findById(command.deliveryId())
                    .orElseThrow(() -> new NotFoundException("出库单", command.deliveryId()));
            if (!delivery.getCustomerId().equals(customer.id())) {
                throw new ConflictException("SALES_RETURN_DELIVERY_MISMATCH", "出库单不属于该客户");
            }
        }
        SalesReturn value = SalesReturn.create(
                "SR-%06d".formatted(returnRepository.nextReturnSequence()), customer.id(),
                command.deliveryId(), command.warehouseId(), command.reason(), command.remark(), currentUserId());
        returnRepository.save(value);
        Map<UUID, BigDecimal> deliveryCosts = deliveryCosts(delivery);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (SalesReturnApi.SaveLineCommand line : command.lines()) {
            masterData.requireProduct(line.productId());
            BigDecimal unitCost = line.unitCost();
            if (unitCost == null && delivery != null) unitCost = deliveryCosts.get(line.productId());
            SalesReturnLine entity;
            try {
                entity = SalesReturnLine.create(value.getId(), line.productId(), line.quantity(),
                        line.unitPrice(), unitCost == null ? BigDecimal.ZERO : unitCost,
                        line.batchNo(), serialNosJson(line.serialNos()));
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("SALES_RETURN_LINE_INVALID", exception.getMessage());
            }
            lineRepository.save(entity);
            total = total.add(entity.getAmount());
            cost = cost.add(entity.getCostAmount());
        }
        try {
            value.totals(total, cost);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("SALES_RETURN_AMOUNT_INVALID", exception.getMessage());
        }
        return toView(returnRepository.save(value));
    }

    @Transactional
    public SalesReturnApi.ReturnView post(UUID id) {
        SalesReturn value = find(id);
        if (value.getStatus() != SalesReturnStatus.DRAFT) {
            throw new ConflictException("SALES_RETURN_STATUS_INVALID", "只有草稿退货单可以过账");
        }
        validateDeliveryLimit(value);
        List<SalesReturnLine> lines = lineRepository.findByReturnIdOrderByCreatedAtAsc(id);
        stockPort.postReturn(new SalesReturnStockPort.Posting(value.getId(), value.getReturnNo(),
                value.getWarehouseId(), lines.stream().map(line -> new SalesReturnStockPort.PostingLine(
                        line.getProductId(), line.getQuantity(), line.getUnitCost(), line.getBatchNo(),
                        parseSerialNos(line.getSerialNos()))).toList()));

        BigDecimal receivableAdjustment = BigDecimal.ZERO.setScale(2);
        BigDecimal refund = BigDecimal.ZERO.setScale(2);
        if (value.getDeliveryId() != null) {
            List<Receivable> receivables = receivableRepository.findByDeliveryId(value.getDeliveryId());
            if (!receivables.isEmpty()) {
                Receivable receivable = receivables.get(0);
                try {
                    refund = receivable.applyReturn(value.getTotalAmount());
                } catch (IllegalStateException exception) {
                    throw new ConflictException("SALES_RETURN_EXCEEDS_RECEIVABLE", exception.getMessage());
                }
                receivableRepository.save(receivable);
                receivableAdjustment = value.getTotalAmount().subtract(refund).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        try {
            value.post(currentUserId(), receivableAdjustment, refund);
        } catch (IllegalStateException exception) {
            throw new ConflictException("SALES_RETURN_STATUS_INVALID", exception.getMessage());
        }
        return toView(returnRepository.save(value));
    }

    @Transactional
    public SalesReturnApi.ReturnView cancel(UUID id) {
        SalesReturn value = find(id);
        try {
            value.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("SALES_RETURN_STATUS_INVALID", exception.getMessage());
        }
        return toView(returnRepository.save(value));
    }

    private void validateDeliveryLimit(SalesReturn value) {
        if (value.getDeliveryId() == null) return;
        SalesDelivery delivery = deliveryRepository.findById(value.getDeliveryId())
                .orElseThrow(() -> new NotFoundException("出库单", value.getDeliveryId()));
        BigDecimal posted = returnRepository.findByDeliveryIdOrderByCreatedAtDesc(value.getDeliveryId()).stream()
                .filter(row -> row.getStatus() == SalesReturnStatus.POSTED)
                .map(SalesReturn::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (posted.add(value.getTotalAmount()).compareTo(delivery.getAmount()) > 0) {
            throw new ConflictException("SALES_RETURN_EXCEEDS_DELIVERY", "累计退货金额不能超过原出库金额");
        }
    }

    private Map<UUID, BigDecimal> deliveryCosts(SalesDelivery delivery) {
        Map<UUID, BigDecimal> costs = new HashMap<>();
        if (delivery == null) return costs;
        for (SalesDeliveryLine line : deliveryLineRepository.findByDeliveryId(delivery.getId())) {
            BigDecimal unit = line.getQuantity().signum() == 0 ? BigDecimal.ZERO
                    : line.getCostAmount().divide(line.getQuantity(), 2, RoundingMode.HALF_UP);
            costs.put(line.getProductId(), unit);
        }
        return costs;
    }

    private SalesReturnApi.ReturnView toView(SalesReturn value) {
        String customerName = masterData.requireCustomer(value.getCustomerId()).name();
        String warehouseName = masterData.requireWarehouse(value.getWarehouseId()).name();
        List<SalesReturnApi.LineView> lines = lineRepository.findByReturnIdOrderByCreatedAtAsc(value.getId())
                .stream().map(line -> {
                    SalesMasterDataPort.ProductInfo product = masterData.requireProduct(line.getProductId());
                    return SalesReturnApi.LineView.from(line, product.productNo(), product.name(),
                            parseSerialNos(line.getSerialNos()));
                }).toList();
        return SalesReturnApi.ReturnView.from(value, customerName, warehouseName, lines);
    }

    private SalesReturn find(UUID id) {
        return returnRepository.findById(id).orElseThrow(() -> new NotFoundException("销售退货单", id));
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
