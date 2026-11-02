package com.ivs.policy.policy.api;

import com.ivs.policy.policy.PolicyEntitlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EntitlementApi(
        UUID id, String entitlementNo, UUID policyId, UUID supplierId, UUID retailId, UUID serialId, UUID productId,
        UUID orderId, BigDecimal faceAmount, BigDecimal claimedAmount, BigDecimal confirmedAmount,
        BigDecimal settledAmount, String periodCode, String status, Instant createdAt,
        String policyName, UUID purchaseOrderId, String purchaseOrderNo, Instant invoiceIssuedAt
) {
    public static EntitlementApi from(PolicyEntitlement e) {
        return new EntitlementApi(e.getId(), e.getEntitlementNo(), e.getPolicyId(), e.getSupplierId(), e.getRetailId(),
                e.getSerialId(), e.getProductId(), e.getOrderId(), e.getFaceAmount(), e.getClaimedAmount(),
                e.getConfirmedAmount(), e.getSettledAmount(), e.getPeriodCode(), e.getStatus(), e.getCreatedAt(),
                null, null, null, null);
    }
}
