package com.ivs.platform.security.api;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** 保存某角色的权限集合（传空集合表示收回该角色全部权限）。 */
public record UpdateRolePermissionsCommand(
        @NotNull(message = "权限集合不能为空") Set<String> permissions
) {
}
