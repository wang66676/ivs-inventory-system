package com.ivs.policy.fund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RebateOffsetLineRepository extends JpaRepository<RebateOffsetLine, UUID> {
    List<RebateOffsetLine> findByOffsetId(UUID offsetId);
}