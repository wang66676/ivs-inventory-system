package com.ivs.platform.organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByCode(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Company> findAllByOrderByNameAsc();
}
