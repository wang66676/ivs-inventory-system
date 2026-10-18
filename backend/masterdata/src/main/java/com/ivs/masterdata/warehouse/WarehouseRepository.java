package com.ivs.masterdata.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);

    List<Warehouse> findAllByOrderByCodeAsc();
}