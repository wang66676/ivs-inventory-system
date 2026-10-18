package com.ivs.retail.retail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetailReversalRepository extends JpaRepository<RetailReversal, UUID> {
    List<RetailReversal> findByRetailIdOrderByReversedAtDesc(UUID retailId);
}