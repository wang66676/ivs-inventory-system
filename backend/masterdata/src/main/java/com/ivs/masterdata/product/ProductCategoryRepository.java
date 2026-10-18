package com.ivs.masterdata.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    Optional<ProductCategory> findByCode(String code);

    boolean existsByCode(String code);

    List<ProductCategory> findAllByOrderBySortOrderAscCodeAsc();
}