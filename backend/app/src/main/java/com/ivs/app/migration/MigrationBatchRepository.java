package com.ivs.app.migration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MigrationBatchRepository extends JpaRepository<MigrationBatch, UUID> {
    List<MigrationBatch> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('ivs.data_migration_no_seq')", nativeQuery = true)
    long nextBatchSequence();
}
