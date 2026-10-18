package com.ivs.platform.security.api;

import java.util.List;

/** 权限分组（用于角色权限页分组勾选）。 */
public record PermissionGroupView(String group, List<PermissionItemView> permissions) {
}
