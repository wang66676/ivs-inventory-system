package com.ivs.policy.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RebateOffsetOrderRepository extends JpaRepository<RebateOffsetOrder, UUID> {
    Optional<RebateOffsetOrder> findByOffsetNo(String offsetNo);

    List<RebateOffsetOrder> findAllByOrderByCreatedAtDesc();

    List<RebateOffsetOrder> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);

    Optional<RebateOffsetOrder> findByPurchaseReceiptId(UUID purchaseReceiptId);

    @Query(value = "select nextval('ivs.rebate_offset_no_seq')", nativeQuery = true)
    long nextOffsetSequence();
}