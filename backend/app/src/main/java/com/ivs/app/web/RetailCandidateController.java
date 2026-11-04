package com.ivs.app.web;

import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.retail.retail.RetailRecordRepository;
import com.ivs.sales.order.SalesOrderLine;
import com.ivs.sales.order.SalesOrderLineRepository;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderRepository;
import com.ivs.sales.order.SalesOrderStatus;
import com.ivs.procurement.purchase.PurchaseOrderApplicationService;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.masterdata.partner.CustomerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 实销候选 VIN：由 app 层组合主数据与实销台账，业务模块之间不产生直接依赖。
 */
@RestController
@RequestMapping("/api/ivs/retail-candidates")
public class RetailCandidateController {

    private final ProductSerialService serialService;
    private final RetailRecordRepository retailRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderApplicationService purchaseOrderService;
    private final CustomerService customerService;

    public RetailCandidateController(ProductSerialService serialService, RetailRecordRepository retailRepository,
                                     SalesOrderLineRepository salesOrderLineRepository,
                                     SalesOrderRepository salesOrderRepository,
                                     PurchaseOrderApplicationService purchaseOrderService,
                                     CustomerService customerService) {
        this.serialService = serialService;
        this.retailRepository = retailRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<RetailCandidateView> list() {
        List<RetailCandidateView> candidates = new java.util.ArrayList<>();
        for (MasterdataApi.SerialView serial : serialService.list(null, null, null)) {
            if (isBattery(serial.categoryCode(), serial.categoryName())) {
                continue;
            }
            if (!Set.of(SerialStatus.LOCKED, SerialStatus.DELIVERED, SerialStatus.SOLD).contains(serial.status())) {
                continue;
            }
            if (retailRepository.findByActiveSerialKey(serial.id()).isPresent()) {
                continue;
            }
            SalesOrder order = findOrder(serial.id());
            if (order == null || !order.isLocked()) {
                continue;
            }
            ProcurementApi.PurchaseOrderLinkView purchaseOrder =
                    purchaseOrderService.findOrderBySerialNo(serial.serialNo());
            String customerName = customerService.find(order.getCustomerId()).getName();
            candidates.add(new RetailCandidateView(serial, order.getId(), order.getExternalOrderNo(),
                    purchaseOrder == null ? null : purchaseOrder.orderId(),
                    purchaseOrder == null ? null
                            : purchaseOrder.externalOrderNo() != null ? purchaseOrder.externalOrderNo()
                            : purchaseOrder.orderNo(),
                    order.getCustomerId(), customerName));
        }
        return candidates;
    }

    private SalesOrder findOrder(UUID serialId) {
        String key = serialId.toString();
        for (SalesOrderLine line : salesOrderLineRepository.searchBySerialId(key)) {
            if (line.getSerialIds() == null || !line.getSerialIds().contains(key)) {
                continue;
            }
            var order = salesOrderRepository.findById(line.getOrderId()).orElse(null);
            if (order != null && order.getStatus() != SalesOrderStatus.CANCELLED) {
                return order;
            }
        }
        return null;
    }

    private boolean isBattery(String categoryCode, String categoryName) {
        String code = categoryCode == null ? "" : categoryCode.toUpperCase();
        String name = categoryName == null ? "" : categoryName;
        return code.equals("BATTERY") || code.startsWith("BATTERY-") || name.contains("电池");
    }

    public record RetailCandidateView(MasterdataApi.SerialView serial, UUID orderId, String orderNo,
                                      UUID purchaseOrderId, String purchaseOrderNo,
                                      UUID customerId, String customerName) {
    }
}
