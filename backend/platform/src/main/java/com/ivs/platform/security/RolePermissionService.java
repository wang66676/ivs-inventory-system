package com.ivs.platform.security;

import com.ivs.platform.security.api.PermissionGroupView;
import com.ivs.platform.security.api.PermissionItemView;
import com.ivs.platform.security.api.RolePermissionCatalogView;
import com.ivs.platform.security.api.RolePermissionView;
import com.ivs.platform.security.api.UpdateRolePermissionsCommand;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 角色权限查询与维护。 */
@Service
public class RolePermissionService {

    private final RolePermissionRepository rolePermissions;

    public RolePermissionService(RolePermissionRepository rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public RolePermissionCatalogView catalog() {
        List<PermissionGroupView> groups = PermissionCatalog.groups().entrySet().stream()
                .map(entry -> new PermissionGroupView(entry.getKey(), entry.getValue().stream()
                        .map(item -> new PermissionItemView(item.code(), item.label()))
                        .toList()))
                .toList();
        return new RolePermissionCatalogView(roles(), groups);
    }

    @Transactional(readOnly = true)
    public List<RolePermissionView> roles() {
        return PermissionCatalog.ROLE_CODES.stream()
                .map(code -> new RolePermissionView(code, PermissionCatalog.ROLE_NAMES.getOrDefault(code, code), permissions(code)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> permissions(String roleCode) {
        requireRole(roleCode);
        return rolePermissions.findByRoleCode(roleCode).stream()
                .map(RolePermission::getPermissionCode)
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public RolePermissionView update(String roleCode, UpdateRolePermissionsCommand command) {
        requireRole(roleCode);
        Set<String> requested = command.permissions();
        Set<String> unknown = requested.stream()
                .filter(code -> !PermissionCatalog.allCodes().contains(code))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!unknown.isEmpty()) {
            throw new ConflictException("PERMISSION_UNKNOWN", "包含不支持的权限：" + String.join("、", unknown));
        }
        // 防止把管理员自己锁在系统外：ADMIN 必须保留用户与权限管理能力
        if ("ADMIN".equals(roleCode) && !requested.contains(PermissionCodes.USER_MANAGE)) {
            throw new ConflictException("ADMIN_PERMISSION_REQUIRED", "系统管理员角色必须保留「用户、角色权限与系统配置管理」权限");
        }
        rolePermissions.deleteByRoleCode(roleCode);
        rolePermissions.flush();
        requested.forEach(code -> rolePermissions.save(RolePermission.grant(roleCode, code)));
        rolePermissions.flush();
        return new RolePermissionView(roleCode, PermissionCatalog.ROLE_NAMES.getOrDefault(roleCode, roleCode), permissions(roleCode));
    }

    private void requireRole(String roleCode) {
        if (roleCode == null || !PermissionCatalog.isAssignable(roleCode)) {
            throw new NotFoundException("角色", roleCode);
        }
    }
}
