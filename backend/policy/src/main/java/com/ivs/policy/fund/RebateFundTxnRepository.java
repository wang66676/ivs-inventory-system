package com.ivs.policy.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RebateFundTxnRepository extends JpaRepository<RebateFundTxn, UUID> {

    List<RebateFundTxn> findBySupplierIdOrderByOccurredAtDesc(UUID supplierId);

    List<RebateFundTxn> findAllByOrderByOccurredAtDesc();

    List<RebateFundTxn> findByOffsetNoOrderByOccurredAtAsc(String offsetNo);
    List<RebateFundTxn> findBySourceTypeAndSourceIdOrderByOccurredAtAsc(String sourceType, UUID sourceId);

    /** 可被抵扣的入池记录（按 FIFO）。 */
    @Query("select t from RebateFundTxn t where t.supplierId = :supplierId and t.direction = com.ivs.policy.fund.FundDirection.IN "
            + "and t.remainingAmount > 0 order by t.occurredAt asc")
    List<RebateFundTxn> findAvailableCredits(@Param("supplierId") UUID supplierId);
}
