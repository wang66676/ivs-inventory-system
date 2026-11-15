package com.ivs.app.orchestration;

import com.ivs.billing.invoice.BillingReferencePort;
import com.ivs.masterdata.partner.CustomerService;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesDeliveryRepository;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderLine;
import com.ivs.sales.order.SalesOrderLineRepository;
import com.ivs.sales.order.SalesOrderRepository;
import com.ivs.sales.order.SalesOrderStatus;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 开票所需的销售侧只读信息（app 层适配）。 */
@Component
public class BillingReferenceAdapter implements BillingReferencePort {

    private final SalesOrderRepository orderRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final CustomerService customerService;
    private final ProductSerialRepository serialRepository;
    private final SalesOrderLineRepository orderLineRepository;

    public BillingReferenceAdapter(SalesOrderRepository orderRepository,
                                   SalesDeliveryRepository deliveryRepository,
                                   CustomerService customerService,
                                   ProductSerialRepository serialRepository,
                                   SalesOrderLineRepository orderLineRepository) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.customerService = customerService;
        this.serialRepository = serialRepository;
        this.orderLineRepository = orderLineRepository;
    }

    @Override
    public OrderInfo requireOrder(UUID orderId) {
        SalesOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("销售订单", orderId));
        return orderInfo(order);
    }

    @Override
    public String customerName(UUID customerId) {
        return customerService.find(customerId).getName();
    }

    @Override
    public Optional<OrderInfo> findOrderBySerialNumbers(List<String> serialNos) {
        Set<UUID> orderIds = new LinkedHashSet<>();
        for (String serialNo : serialNos) {
            String normalized = serialNo == null ? "" : serialNo.trim().toUpperCase();
            serialRepository.findBySerialNo(normalized).ifPresent(serial -> {
                String serialId = serial.getId().toString();
                for (SalesOrderLine line : orderLineRepository.searchBySerialId(serialId)) {
                    if (line.getSerialIds() != null && line.getSerialIds().contains(serialId)) {
                        orderRepository.findById(line.getOrderId())
                                .filter(order -> order.getStatus() != SalesOrderStatus.CANCELLED)
                                .ifPresent(order -> orderIds.add(order.getId()));
                    }
                }
            });
        }
        if (orderIds.size() != 1) return Optional.empty();
        return orderRepository.findById(orderIds.iterator().next()).map(this::orderInfo);
    }

    @Override
    public Optional<OrderInfo> findOrderByOrderNos(List<String> orderNos) {
        for (String orderNo : orderNos) {
            String value = orderNo == null ? "" : orderNo.trim();
            Optional<SalesOrder> order = orderRepository.findByOrderNo(value);
            if (order.isEmpty()) order = orderRepository.findByExternalOrderNo(value);
            if (order.isPresent() && order.get().getStatus() != SalesOrderStatus.CANCELLED) {
                return Optional.of(orderInfo(order.get()));
            }
        }
        return Optional.empty();
    }

    private OrderInfo orderInfo(SalesOrder order) {
        BigDecimal deliveredAmount = deliveryRepository.findByOrderIdOrderByDeliveredAtAsc(order.getId()).stream()
                .map(SalesDelivery::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new OrderInfo(order.getId(), order.getOrderNo(), order.getCustomerId(),
                customerName(order.getCustomerId()), deliveredAmount);
    }
}