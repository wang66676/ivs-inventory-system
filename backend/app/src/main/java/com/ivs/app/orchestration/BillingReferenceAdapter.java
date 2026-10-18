package com.ivs.app.orchestration;

import com.ivs.billing.invoice.BillingReferencePort;
import com.ivs.masterdata.partner.CustomerService;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesDeliveryRepository;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderRepository;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** 开票所需的销售侧只读信息（app 层适配）。 */
@Component
public class BillingReferenceAdapter implements BillingReferencePort {

    private final SalesOrderRepository orderRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final CustomerService customerService;

    public BillingReferenceAdapter(SalesOrderRepository orderRepository,
                                   SalesDeliveryRepository deliveryRepository,
                                   CustomerService customerService) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.customerService = customerService;
    }

    @Override
    public OrderInfo requireOrder(UUID orderId) {
        SalesOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("销售订单", orderId));
        BigDecimal deliveredAmount = deliveryRepository.findByOrderIdOrderByDeliveredAtAsc(orderId).stream()
                .map(SalesDelivery::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new OrderInfo(order.getId(), order.getOrderNo(), order.getCustomerId(),
                customerName(order.getCustomerId()), deliveredAmount);
    }

    @Override
    public String customerName(UUID customerId) {
        return customerService.find(customerId).getName();
    }
}