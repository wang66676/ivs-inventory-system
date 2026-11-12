package com.ivs.policy.fund.api;

import com.ivs.policy.fund.RebateFundService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 返利资金池：入池 / 预览 / 抵扣 / 取消抵扣 / 流水。 */
@RestController
@RequestMapping("/api/ivs/rebate-fund-pool")
public class RebateFundController {

    private final RebateFundService service;

    public RebateFundController(RebateFundService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_FUND_POOL_VIEW')")
    public RebateFundApi.PoolView overview() {
        return service.overview();
    }

    @GetMapping("/txns")
    @PreAuthorize("hasAuthority('IVS_FUND_POOL_VIEW')")
    public List<RebateFundApi.TxnView> txns(@RequestParam(required = false) UUID supplierId) {
        return service.txns(supplierId);
    }

    @GetMapping("/offsets")
    @PreAuthorize("hasAnyAuthority('IVS_FUND_POOL_VIEW', 'IVS_FUND_OFFSET')")
    public List<RebateFundApi.OffsetView> offsets(@RequestParam(required = false) UUID supplierId) {
        return service.offsets(supplierId);
    }

    /** 抵扣预览：可用余额 + 本次可抵金额 + 可勾选的入池记录。 */
    @GetMapping("/offset-preview")
    @PreAuthorize("hasAuthority('IVS_FUND_OFFSET')")
    public RebateFundApi.OffsetPreview preview(@RequestParam UUID supplierId,
                                               @RequestParam(required = false) BigDecimal maxAmount) {
        return service.preview(supplierId, maxAmount);
    }

    /** 登记返利入池（已到账：红字发票 / 厂家打款 / 权益结算）。 */
    @PostMapping("/credits")
    @PreAuthorize("hasAuthority('IVS_FUND_POOL_ADJUST')")
    public RebateFundApi.PoolView credit(@Valid @RequestBody RebateFundApi.CreditCommand command) {
        return service.credit(command);
    }

    /** 人工调整入池申请：达到阈值时审批通过后才入池。 */
    @PostMapping("/credits/request")
    @PreAuthorize("hasAuthority('IVS_FUND_POOL_VIEW')")
    public RebateFundApi.PoolView requestCredit(@Valid @RequestBody RebateFundApi.CreditCommand command) {
        return service.submitCredit(command);
    }

    /** 抵扣车款（无需审批；通常由入库单确认时调用）。 */
    @PostMapping("/offsets")
    @PreAuthorize("hasAuthority('IVS_FUND_OFFSET')")
    public RebateFundApi.OffsetResult offset(@RequestBody OffsetRequest request) {
        return service.offset(request.supplierId(), request.purchaseOrderId(), request.purchaseReceiptId(),
                request.receiptNo(), request.maxAmount(), request.fundTxnIds());
    }

    /** 兼容独立抵扣；关联采购收货的抵扣必须走 app 层返利抵扣工作台。 */
    @PostMapping("/offsets/{offsetId}/cancel")
    @PreAuthorize("hasAuthority('IVS_FUND_OFFSET')")
    public RebateFundApi.OffsetView cancel(@PathVariable("offsetId") UUID offsetId) {
        return service.cancelOffset(offsetId);
    }

    public record OffsetRequest(UUID supplierId, UUID purchaseOrderId, UUID purchaseReceiptId, String receiptNo,
                                BigDecimal maxAmount, List<UUID> fundTxnIds) {
    }
}
