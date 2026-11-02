package com.ivs.app.orchestration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.policy.reference.PreEntitlementReferencePort;
import com.ivs.procurement.purchase.PurchaseOrder;
import com.ivs.procurement.purchase.PurchaseOrderRepository;
import com.ivs.procurement.purchase.PurchaseReceipt;
import com.ivs.procurement.purchase.PurchaseReceiptLineRepository;
import com.ivs.procurement.purchase.PurchaseReceiptRepository;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.retail.retail.RetailRecord;
import com.ivs.retail.retail.RetailRecordRepository;
import com.ivs.retail.retail.RetailStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/** 由 app 层补齐预享受项的采购订单编号和对应实销日期。 */
@Component
public class PreEntitlementReferenceAdapter implements PreEntitlementReferencePort {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseReceiptLineRepository receiptLineRepository;
    private final RetailRecordRepository retailRepository;
    private final ProductSerialRepository serialRepository;
    private final ObjectMapper objectMapper;

    public PreEntitlementReferenceAdapter(PurchaseOrderRepository orderRepository,
                                          PurchaseReceiptRepository receiptRepository,
                                          PurchaseReceiptLineRepository receiptLineRepository,
                                          RetailRecordRepository retailRepository,
                                          ProductSerialRepository serialRepository,
                                          ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.receiptRepository = receiptRepository;
        this.receiptLineRepository = receiptLineRepository;
        this.retailRepository = retailRepository;
        this.serialRepository = serialRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReference describe(UUID orderId, UUID orderLineId, UUID serialId) {
        if (orderId == null) {
            return new OrderReference(null, null, null, null);
        }
        PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return new OrderReference(null, null, null, null);
        }
        String orderNo = order.getExternalOrderNo() == null || order.getExternalOrderNo().isBlank()
                ? order.getOrderNo() : order.getExternalOrderNo();
        Set<String> serialNos = new HashSet<>();
        if (serialId != null) {
            serialRepository.findById(serialId).map(serial -> serial.getSerialNo()).ifPresent(serialNos::add);
        } else {
            for (PurchaseReceipt receipt : receiptRepository.findByOrderIdOrderByReceivedAtAsc(orderId)) {
                receiptLineRepository.findByReceiptId(receipt.getId()).stream()
                        .filter(line -> orderLineId == null || orderLineId.equals(line.getOrderLineId()))
                        .forEach(line -> serialNos.addAll(parse(line.getSerialNos())));
            }
        }
        List<RetailRecord> retails = retailRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(record -> record.getStatus() != RetailStatus.REVERSED)
                .filter(record -> serialNos.contains(record.getSerialNo()))
                .toList();
        LocalDate retailDate = retails.stream().map(RetailRecord::getRetailDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
        Instant invoiceIssuedAt = retails.stream().map(RetailRecord::getInvoiceIssuedAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        String serialText = serialNos.isEmpty() ? null : serialNos.stream().sorted().reduce((a, b) -> a + "、" + b).orElse(null);
        return new OrderReference(serialText, orderNo, invoiceIssuedAt, retailDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceivedSerial> receivedSerials(UUID orderId) {
        if (orderId == null) return List.of();
        List<ReceivedSerial> rows = new ArrayList<>();
        Set<UUID> seenSerialIds = new HashSet<>();
        for (PurchaseReceipt receipt : receiptRepository.findByOrderIdOrderByReceivedAtAsc(orderId)) {
            for (var line : receiptLineRepository.findByReceiptId(receipt.getId())) {
                if (line.getOrderLineId() == null) continue;
                for (String serialNo : parse(line.getSerialNos())) {
                    serialRepository.findBySerialNo(serialNo.trim().toUpperCase()).ifPresent(serial -> {
                        if (seenSerialIds.add(serial.getId())) {
                            rows.add(new ReceivedSerial(line.getOrderLineId(), line.getProductId(),
                                    line.getUnitPrice(), serial.getId()));
                        }
                    });
                }
            }
        }
        return rows;
    }

    private List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }
}
