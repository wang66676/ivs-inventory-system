package com.ivs.platform.security.api;

import java.util.List;

/** 角色权限页初始化数据：角色清单 + 权限目录 + 当前授权。 */
public record RolePermissionCatalogView(
        List<RolePermissionView> roles,
        List<PermissionGroupView> groups
) {
}
