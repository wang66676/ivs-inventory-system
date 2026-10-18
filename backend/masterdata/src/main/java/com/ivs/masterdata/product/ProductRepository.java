package com.ivs.masterdata.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByProductNo(String productNo);

    boolean existsByProductNo(String productNo);

    long countByCategoryId(UUID categoryId);

    List<Product> findAllByOrderByCreatedAtDesc();

    List<Product> findByCategoryIdOrderByCreatedAtDesc(UUID categoryId);

    @Query("select p from Product p where lower(p.name) like :keyword or lower(p.productNo) like :keyword "
            + "or lower(coalesce(p.brand, '')) like :keyword or lower(coalesce(p.series, '')) like :keyword "
            + "order by p.createdAt desc")
    List<Product> search(@Param("keyword") String keyword);

    /** 主数据编码序列（H2 与 PostgreSQL 均支持 nextval）。 */
    @Query(value = "select nextval('ivs.product_no_seq')", nativeQuery = true)
    long nextProductSequence();
}