package com.ivs.billing.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RefundOrderRepository extends JpaRepository<RefundOrder, UUID> {
    List<RefundOrder> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('ivs.refund_no_seq')", nativeQuery = true)
    long nextRefundSequence();
}
