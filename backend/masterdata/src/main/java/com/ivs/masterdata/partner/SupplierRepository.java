package com.ivs.masterdata.partner;

import com.ivs.masterdata.SupplierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Optional<Supplier> findByCode(String code);

    boolean existsByCode(String code);

    List<Supplier> findAllByOrderByCodeAsc();

    List<Supplier> findBySupplierTypeOrderByCodeAsc(SupplierType supplierType);

    @Query(value = "select nextval('ivs.supplier_no_seq')", nativeQuery = true)
    long nextSupplierSequence();
}