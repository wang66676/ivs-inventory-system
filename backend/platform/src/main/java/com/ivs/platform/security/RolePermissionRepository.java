package com.ivs.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, java.util.UUID> {
    List<RolePermission> findByRoleCodeIn(List<String> roleCodes);

    List<RolePermission> findByRoleCode(String roleCode);

    void deleteByRoleCode(String roleCode);

    boolean existsByRoleCodeAndPermissionCode(String roleCode, String permissionCode);
}
