package com.ivs.app.orchestration;

import com.ivs.policy.policy.PolicyApplicationService;
import com.ivs.policy.policy.api.PolicyApi;
import com.ivs.procurement.purchase.PurchasePreEntitlementPort;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/** 采购单保存后，由 app 层把选中的返利政策同步为预享受项。 */
@Component
public class PurchasePreEntitlementAdapter implements PurchasePreEntitlementPort {

    private final PolicyApplicationService policyService;
    private final ProductSerialRepository serialRepository;

    public PurchasePreEntitlementAdapter(PolicyApplicationService policyService,
                                         ProductSerialRepository serialRepository) {
        this.policyService = policyService;
        this.serialRepository = serialRepository;
    }

    @Override
    public void clear(UUID purchaseOrderId) {
        policyService.clearFromPurchaseOrder(purchaseOrderId);
    }

    @Override
    public void sync(UUID purchaseOrderId, List<PreEntitlementLine> lines) {
        List<PolicyApi.SyncPreEntitlementLine> syncLines = lines.stream()
                .map(line -> new PolicyApi.SyncPreEntitlementLine(line.orderLineId(), null, line.productId(),
                        line.unitPrice(), line.policyIds()))
                .toList();
        policyService.syncFromPurchaseOrder(purchaseOrderId, syncLines);
    }

    @Override
    public void syncSerials(UUID purchaseOrderId, UUID orderLineId, UUID productId, java.math.BigDecimal unitPrice,
                            List<UUID> policyIds, List<String> serialNos) {
        java.util.List<PolicyApi.SyncPreEntitlementLine> lines = new ArrayList<>();
        for (String serialNo : serialNos) {
            ProductSerial serial = serialRepository.findBySerialNo(serialNo.trim().toUpperCase())
                    .orElseThrow(() -> new NotFoundException("序列号档案", serialNo));
            lines.add(new PolicyApi.SyncPreEntitlementLine(orderLineId, serial.getId(), productId, unitPrice, policyIds));
        }
        policyService.syncReceivedSerials(purchaseOrderId, lines);
    }
}
