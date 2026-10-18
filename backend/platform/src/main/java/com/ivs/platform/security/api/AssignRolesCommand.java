package com.ivs.platform.security.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AssignRolesCommand(
        @NotEmpty(message = "至少选择一个角色") Set<String> roles
) {
}
