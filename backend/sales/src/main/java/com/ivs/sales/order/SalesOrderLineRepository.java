package com.ivs.sales.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, UUID> {
    List<SalesOrderLine> findByOrderIdOrderByLineNoAsc(UUID orderId);

    @Query("select l from SalesOrderLine l where lower(coalesce(l.serialIds, '')) like lower(concat('%', :serialId, '%'))")
    List<SalesOrderLine> searchBySerialId(@Param("serialId") String serialId);
}
