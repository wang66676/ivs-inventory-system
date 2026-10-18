package com.ivs.platform.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "platform_role_permission", schema = "ivs",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_code", "permission_code"}))
public class RolePermission {

    @Id
    private UUID id;

    @Column(name = "role_code", nullable = false, length = 60)
    private String roleCode;

    @Column(name = "permission_code", nullable = false, length = 80)
    private String permissionCode;

    protected RolePermission() {
    }

    public static RolePermission grant(String roleCode, String permissionCode) {
        RolePermission permission = new RolePermission();
        permission.id = UUID.randomUUID();
        permission.roleCode = roleCode;
        permission.permissionCode = permissionCode;
        return permission;
    }

    public String getRoleCode() { return roleCode; }
    public String getPermissionCode() { return permissionCode; }
}
