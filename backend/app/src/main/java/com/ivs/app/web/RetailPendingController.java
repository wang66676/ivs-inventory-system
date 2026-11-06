package com.ivs.app.web;

import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.CustomerService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.procurement.purchase.PurchaseOrderApplicationService;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.retail.retail.RetailRecordRepository;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesDeliveryLine;
import com.ivs.sales.order.SalesDeliveryLineRepository;
import com.ivs.sales.order.SalesDeliveryRepository;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderLine;
import com.ivs.sales.order.SalesOrderLineRepository;
import com.ivs.sales.order.SalesOrderRepository;
import com.ivs.sales.order.SalesOrderStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 已交付但尚未完成实销的车辆预警。 */
@RestController
@RequestMapping("/api/ivs/retail-pending")
public class RetailPendingController {

    private final ProductSerialService serialService;
    private final RetailRecordRepository retailRepository;
    private final SalesOrderLineRepository orderLineRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesDeliveryLineRepository deliveryLineRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final PurchaseOrderApplicationService purchaseOrderService;
    private final CustomerService customerService;
    private final ProductService productService;

    public RetailPendingController(ProductSerialService serialService,
                                   RetailRecordRepository retailRepository,
                                   SalesOrderLineRepository orderLineRepository,
                                   SalesOrderRepository orderRepository,
                                   SalesDeliveryLineRepository deliveryLineRepository,
                                   SalesDeliveryRepository deliveryRepository,
                                   PurchaseOrderApplicationService purchaseOrderService,
                                   CustomerService customerService,
                                   ProductService productService) {
        this.serialService = serialService;
        this.retailRepository = retailRepository;
        this.orderLineRepository = orderLineRepository;
        this.orderRepository = orderRepository;
        this.deliveryLineRepository = deliveryLineRepository;
        this.deliveryRepository = deliveryRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.customerService = customerService;
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<PendingView> list() {
        List<PendingView> result = new ArrayList<>();
        Instant now = Instant.now();
        for (MasterdataApi.SerialView serial : serialService.list(null, null, null)) {
            if (isBattery(serial.categoryCode(), serial.categoryName())) continue;
            if (!Set.of(SerialStatus.DELIVERED, SerialStatus.SOLD).contains(serial.status())) continue;
            if (retailRepository.findByActiveSerialKey(serial.id()).isPresent()) continue;
            SalesOrder order = findOrder(serial.id());
            if (order == null) continue;
            SalesDelivery delivery = findDelivery(order, serial.id());
            if (delivery == null) continue;
            ProcurementApi.PurchaseOrderLinkView purchaseOrder =
                    purchaseOrderService.findOrderBySerialNo(serial.serialNo());
            long days = Math.max(0, Duration.between(delivery.getDeliveredAt(), now).toDays());
            result.add(new PendingView(serial, order.getId(), order.getExternalOrderNo(),
                    delivery.getId(), delivery.getDeliveryNo(), delivery.getDeliveredAt(), days,
                    productService.get(serial.productId()).series(),
                    order.getCustomerId(), customerService.find(order.getCustomerId()).getName(),
                    purchaseOrder == null ? null : purchaseOrder.orderId(),
                    purchaseOrder == null ? null : purchaseOrder.externalOrderNo() != null
                            ? purchaseOrder.externalOrderNo() : purchaseOrder.orderNo()));
        }
        result.sort(Comparator.comparingLong(PendingView::pendingDays).reversed());
        return result;
    }

    private SalesOrder findOrder(UUID serialId) {
        String key = serialId.toString();
        for (SalesOrderLine line : orderLineRepository.searchBySerialId(key)) {
            if (line.getSerialIds() == null || !line.getSerialIds().contains(key)) continue;
            SalesOrder order = orderRepository.findById(line.getOrderId()).orElse(null);
            if (order != null && order.getStatus() != SalesOrderStatus.CANCELLED) return order;
        }
        return null;
    }

    private SalesDelivery findDelivery(SalesOrder order, UUID serialId) {
        String key = serialId.toString();
        for (SalesOrderLine orderLine : orderLineRepository.findByOrderIdOrderByLineNoAsc(order.getId())) {
            for (SalesDeliveryLine line : deliveryLineRepository.findByOrderLineId(orderLine.getId())) {
                if (line.getSerialIds() != null && line.getSerialIds().contains(key)) {
                    return deliveryRepository.findById(line.getDeliveryId()).orElse(null);
                }
            }
        }
        return null;
    }

    private boolean isBattery(String categoryCode, String categoryName) {
        String code = categoryCode == null ? "" : categoryCode.toUpperCase();
        return code.equals("BATTERY") || code.startsWith("BATTERY-") || (categoryName != null && categoryName.contains("电池"));
    }

    public record PendingView(MasterdataApi.SerialView serial, UUID orderId, String orderNo,
                              UUID deliveryId, String deliveryNo, Instant deliveredAt, long pendingDays,
                              String series, UUID customerId, String customerName, UUID purchaseOrderId,
                              String purchaseOrderNo) {
    }
}
