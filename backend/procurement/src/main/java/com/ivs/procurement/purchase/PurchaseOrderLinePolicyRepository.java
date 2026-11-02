package com.ivs.procurement.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderLinePolicyRepository extends JpaRepository<PurchaseOrderLinePolicy, UUID> {
    List<PurchaseOrderLinePolicy> findByOrderLineIdInOrderByOrderLineIdAscSortNoAsc(Collection<UUID> orderLineIds);

    List<PurchaseOrderLinePolicy> findByOrderLineIdOrderBySortNoAsc(UUID orderLineId);

    void deleteByOrderLineIdIn(Collection<UUID> orderLineIds);
}
