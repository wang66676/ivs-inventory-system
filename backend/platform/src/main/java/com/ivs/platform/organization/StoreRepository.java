package com.ivs.platform.organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface StoreRepository extends JpaRepository<Store, UUID> {
    boolean existsByCompanyIdAndCode(UUID companyId, String code);
    List<Store> findAllByOrderByNameAsc();
    List<Store> findByCompanyIdOrderByNameAsc(UUID companyId);
}
