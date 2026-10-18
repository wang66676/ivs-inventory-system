package com.ivs.masterdata.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAttrDefRepository extends JpaRepository<ProductAttrDef, UUID> {
    List<ProductAttrDef> findByCategoryIdOrderBySortOrderAscCodeAsc(UUID categoryId);

    boolean existsByCategoryIdAndCode(UUID categoryId, String code);
    void deleteByCategoryId(UUID categoryId);
}
