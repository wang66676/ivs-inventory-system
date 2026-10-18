package com.ivs.app.orchestration;

import com.ivs.policy.policy.PromotionPolicy;
import com.ivs.policy.policy.PromotionPolicyRepository;
import com.ivs.procurement.purchase.PurchasePolicyPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 采购明细选择返利政策时，由 app 层适配政策模块的只读查询。 */
@Component
public class PurchasePolicyAdapter implements PurchasePolicyPort {

    private final PromotionPolicyRepository policyRepository;

    public PurchasePolicyAdapter(PromotionPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Override
    public List<PolicyInfo> findByIds(Collection<UUID> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) return List.of();
        return policyRepository.findAllById(policyIds).stream()
                .map(this::toInfo)
                .toList();
    }

    private PolicyInfo toInfo(PromotionPolicy policy) {
        return new PolicyInfo(policy.getId(), policy.getPolicyNo(), policy.getName(),
                policy.getSupplierId(), policy.getStatus().name());
    }
}
