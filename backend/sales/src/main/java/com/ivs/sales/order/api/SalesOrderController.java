package com.ivs.sales.order.api;

import com.ivs.sales.order.SalesOrderApplicationService;
import com.ivs.sales.order.SalesOrderStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 销售订单与出库。 */
@RestController
@RequestMapping("/api/ivs/sales-orders")
public class SalesOrderController {

    private final SalesOrderApplicationService service;

    public SalesOrderController(SalesOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<SalesApi.SalesOrderView> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) SalesOrderStatus status,
                                              @RequestParam(required = false) UUID customerId) {
        return service.list(keyword, status, customerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public SalesApi.SalesOrderView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<SalesApi.SalesOrderView> create(@Valid @RequestBody SalesApi.SaveSalesOrderCommand command) {
        SalesApi.SalesOrderView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/sales-orders/" + view.id())).body(view);
    }

    /** 草稿编辑：补全 VIN、客户、仓库与备注，锁货后不可修改。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.SalesOrderView update(@PathVariable("id") UUID id,
                                          @Valid @RequestBody SalesApi.SaveSalesOrderCommand command) {
        return service.update(id, command);
    }

    /** 已确认但尚未出库的订单补录成交价，避免零价订单在出库时才失败。 */
    @PutMapping("/{id}/prices")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.SalesOrderView updatePrices(@PathVariable("id") UUID id,
                                                @RequestBody SalesApi.UpdatePricesCommand command) {
        return service.updatePrices(id, command);
    }

    /** 价格审批：低于最低限价的订单必须先审批才能确认。 */
    @PostMapping("/{id}/approve-price")
    @PreAuthorize("hasAuthority('IVS_PRICE_APPROVE')")
    public SalesApi.SalesOrderView approvePrice(@PathVariable("id") UUID id) {
        return service.approvePrice(id);
    }

    /** 价格审批驳回：必须填写原因，订单保留为草稿等待申请人修改。 */
    @PostMapping("/{id}/reject-price")
    @PreAuthorize("hasAuthority('IVS_PRICE_APPROVE')")
    public SalesApi.SalesOrderView rejectPrice(@PathVariable("id") UUID id,
                                               @RequestBody Map<String, String> body) {
        return service.rejectPrice(id, body.get("reason"));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.SalesOrderView confirm(@PathVariable("id") UUID id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.SalesOrderView lock(@PathVariable("id") UUID id,
                                        @RequestBody(required = false) SalesApi.DeliverCommand command) {
        return service.lockOrder(id, command == null ? null : command.warehouseId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.SalesOrderView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        service.deleteCancelled(id);
        return ResponseEntity.noContent().build();
    }

    /** 出库：序列号商品按 VIN 逐台出库，数量商品按数量出库。 */
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.SalesDeliveryView deliver(@PathVariable("id") UUID id,
                                              @Valid @RequestBody SalesApi.DeliverCommand command) {
        return service.deliver(id, command);
    }
}
