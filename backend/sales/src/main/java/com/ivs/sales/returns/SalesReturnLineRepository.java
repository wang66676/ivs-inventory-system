package com.ivs.sales.returns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesReturnLineRepository extends JpaRepository<SalesReturnLine, UUID> {
    List<SalesReturnLine> findByReturnIdOrderByCreatedAtAsc(UUID returnId);
}
