package com.ivs.app.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.policy.policy.PolicyApplicationService;
import com.ivs.policy.policy.api.EntitlementApi;
import com.ivs.procurement.purchase.PurchaseOrderLine;
import com.ivs.procurement.purchase.PurchaseOrderLineRepository;
import com.ivs.procurement.purchase.PurchaseReceiptLine;
import com.ivs.procurement.purchase.PurchaseReceiptLineRepository;
import com.ivs.shared.error.ConflictException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 实销后生成权益：app 层按 VIN 反查采购订单，使采购政策同步的预享受项可被兑现。 */
@RestController
@RequestMapping("/api/ivs/retail-entitlements")
public class RetailEntitlementController {

    private final PolicyApplicationService policyService;
    private final ProductSerialService serialService;
    private final PurchaseReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ObjectMapper objectMapper;

    public RetailEntitlementController(PolicyApplicationService policyService, ProductSerialService serialService,
                                       PurchaseReceiptLineRepository receiptLineRepository,
                                       PurchaseOrderLineRepository purchaseOrderLineRepository,
                                       ObjectMapper objectMapper) {
        this.policyService = policyService;
        this.serialService = serialService;
        this.receiptLineRepository = receiptLineRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('IVS_ENTITLEMENT_APPLY')")
    public List<EntitlementApi> generate(@RequestBody GenerateCommand command) {
        UUID purchaseOrderId = resolvePurchaseOrderId(command.serialId());
        // 实销页传入的是销售订单 ID；采购返利预享受项必须始终回落到 VIN 对应的采购订单。
        UUID targetOrderId = purchaseOrderId != null ? purchaseOrderId : command.orderId();
        if (targetOrderId == null) {
            throw new ConflictException("RETAIL_ENTITLEMENT_ORDER_REQUIRED", "未找到可生成权益的采购订单或销售订单");
        }
        return policyService.generateFromRetail(targetOrderId, command.retailId(), command.retailDate(),
                command.serialId());
    }

    private UUID resolvePurchaseOrderId(UUID serialId) {
        if (serialId == null) {
            return null;
        }
        String serialNo = serialService.get(serialId).serialNo();
        for (PurchaseReceiptLine receiptLine : receiptLineRepository.searchBySerialNo(serialNo)) {
            if (!parse(receiptLine.getSerialNos()).contains(serialNo)) {
                continue;
            }
            if (receiptLine.getOrderLineId() == null) {
                continue;
            }
            PurchaseOrderLine orderLine = purchaseOrderLineRepository.findById(receiptLine.getOrderLineId()).orElse(null);
            if (orderLine != null) {
                return orderLine.getOrderId();
            }
        }
        return null;
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

    public record GenerateCommand(UUID orderId, UUID retailId, LocalDate retailDate, UUID serialId) {
    }
}
