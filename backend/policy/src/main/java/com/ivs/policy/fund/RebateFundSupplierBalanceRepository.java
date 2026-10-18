package com.ivs.policy.fund;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RebateFundSupplierBalanceRepository extends JpaRepository<RebateFundSupplierBalance, UUID> {

    Optional<RebateFundSupplierBalance> findByPoolIdAndSupplierId(UUID poolId, UUID supplierId);

    /** 抵扣/入池前取行锁，防止并发超发与透支。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RebateFundSupplierBalance b where b.poolId = :poolId and b.supplierId = :supplierId")
    Optional<RebateFundSupplierBalance> findForUpdate(@Param("poolId") UUID poolId,
                                                      @Param("supplierId") UUID supplierId);

    List<RebateFundSupplierBalance> findByPoolIdOrderBySupplierIdAsc(UUID poolId);
}