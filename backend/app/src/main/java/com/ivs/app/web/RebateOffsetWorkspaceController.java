package com.ivs.app.web;

import com.ivs.app.orchestration.RebateOffsetCancellationService;
import com.ivs.policy.fund.api.RebateFundApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 返利抵扣工作台：取消抵扣时同步恢复采购收货与应付。 */
@RestController
@RequestMapping("/api/ivs/rebate-offset-workspace")
public class RebateOffsetWorkspaceController {

    private final RebateOffsetCancellationService service;

    public RebateOffsetWorkspaceController(RebateOffsetCancellationService service) {
        this.service = service;
    }

    @PostMapping("/{offsetId}/cancel")
    @PreAuthorize("hasAuthority('IVS_FUND_OFFSET')")
    public RebateFundApi.OffsetView cancel(@PathVariable("offsetId") UUID offsetId) {
        return service.cancel(offsetId);
    }
}
