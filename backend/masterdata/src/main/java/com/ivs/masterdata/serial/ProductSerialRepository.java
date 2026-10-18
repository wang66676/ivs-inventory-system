package com.ivs.masterdata.serial;

import com.ivs.masterdata.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSerialRepository extends JpaRepository<ProductSerial, UUID> {

    Optional<ProductSerial> findBySerialNo(String serialNo);

    boolean existsBySerialNo(String serialNo);

    long countByProductIdAndStatus(UUID productId, SerialStatus status);

    List<ProductSerial> findAllByOrderByCreatedAtDesc();

    List<ProductSerial> findByStatusOrderByCreatedAtDesc(SerialStatus status);

    List<ProductSerial> findByWarehouseIdAndStatusOrderByCreatedAtDesc(UUID warehouseId, SerialStatus status);

    @Query("select s from ProductSerial s where lower(s.serialNo) like :keyword "
            + "or lower(coalesce(s.batchNo, '')) like :keyword order by s.createdAt desc")
    List<ProductSerial> search(@Param("keyword") String keyword);

    /** 状态分布，用于产品大项维度的档案统计。 */
    @Query("select s.status, count(s) from ProductSerial s group by s.status")
    List<Object[]> countByStatus();
}