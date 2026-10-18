package com.ivs.retail.retail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetailRecordRepository extends JpaRepository<RetailRecord, UUID> {

    Optional<RetailRecord> findByRetailNo(String retailNo);

    Optional<RetailRecord> findByActiveSerialKey(UUID serialId);

    List<RetailRecord> findAllByOrderByCreatedAtDesc();

    List<RetailRecord> findByStatusOrderByCreatedAtDesc(RetailStatus status);

    List<RetailRecord> findByRetailDateBetweenOrderByRetailDateDesc(LocalDate from, LocalDate to);

    @Query(value = "select nextval('ivs.retail_no_seq')", nativeQuery = true)
    long nextRetailSequence();
}