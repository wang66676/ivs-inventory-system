package com.ivs.sales.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository extends JpaRepository<Receivable, UUID> {

    Optional<Receivable> findByReceivableNo(String receivableNo);

    List<Receivable> findAllByOrderByCreatedAtDesc();

    List<Receivable> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Receivable> findByOrderId(UUID orderId);

    List<Receivable> findByDeliveryId(UUID deliveryId);

    /** 按到期日 FIFO 取未结清应收，用于收款核销。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receivable r where r.customerId = :customerId and r.status <> com.ivs.sales.settlement.ReceivableStatus.SETTLED "
            + "order by coalesce(r.dueDate, r.createdAt) asc")
    List<Receivable> findOutstandingForUpdate(@Param("customerId") UUID customerId);

    List<Receivable> findByStatusOrderByDueDateAsc(ReceivableStatus status);

    @Query(value = "select nextval('ivs.receivable_no_seq')", nativeQuery = true)
    long nextReceivableSequence();
}