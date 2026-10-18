package com.ivs.platform.organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface TeamRepository extends JpaRepository<Team, UUID> {
    boolean existsByStoreIdAndCode(UUID storeId, String code);
    List<Team> findAllByOrderByNameAsc();
    List<Team> findByStoreIdOrderByNameAsc(UUID storeId);
}
