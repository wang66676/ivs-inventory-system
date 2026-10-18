package com.ivs.platform.security.api;

import java.util.Set;

/** 角色及其权限。 */
public record RolePermissionView(String roleCode, String roleName, Set<String> permissions) {
}
