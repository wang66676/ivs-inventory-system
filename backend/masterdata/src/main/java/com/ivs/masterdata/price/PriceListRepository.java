package com.ivs.masterdata.price;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {
    Optional<PriceList> findByCode(String code);

    boolean existsByCode(String code);

    List<PriceList> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('ivs.price_list_no_seq')", nativeQuery = true)
    long nextPriceListSequence();
}