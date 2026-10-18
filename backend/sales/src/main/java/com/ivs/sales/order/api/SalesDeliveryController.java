package com.ivs.sales.order.api;

import com.ivs.sales.order.SalesOrderApplicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 出库单查询。 */
@RestController
@RequestMapping("/api/ivs/sales-deliveries")
public class SalesDeliveryController {

    private final SalesOrderApplicationService service;

    public SalesDeliveryController(SalesOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<SalesApi.SalesDeliveryView> list() {
        return service.listDeliveries();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public SalesApi.SalesDeliveryView get(@PathVariable("id") UUID id) {
        return service.getDelivery(id);
    }
}