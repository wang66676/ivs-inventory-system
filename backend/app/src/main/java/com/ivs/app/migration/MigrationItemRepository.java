package com.ivs.app.migration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MigrationItemRepository extends JpaRepository<MigrationItem, UUID> {
    List<MigrationItem> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
    List<MigrationItem> findByBatchIdAndItemTypeOrderByCreatedAtAsc(UUID batchId, String itemType);
}
