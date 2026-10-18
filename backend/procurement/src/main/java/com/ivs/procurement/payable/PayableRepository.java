package com.ivs.procurement.payable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayableRepository extends JpaRepository<Payable, UUID> {
    Optional<Payable> findByPurchaseReceiptId(UUID receiptId);
    Optional<Payable> findByPayableNo(String payableNo);
    List<Payable> findAllByOrderByCreatedAtDesc();
    List<Payable> findBySupplierIdOrderByDueDateAscCreatedAtAsc(UUID supplierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payable p where p.supplierId = :supplierId "
            + "and p.status <> com.ivs.procurement.payable.PayableStatus.SETTLED "
            + "order by coalesce(p.dueDate, current_date) asc, p.createdAt asc")
    List<Payable> findOutstandingForUpdate(@Param("supplierId") UUID supplierId);

    @Query(value = "select nextval('ivs.payable_no_seq')", nativeQuery = true)
    long nextPayableSequence();
}
