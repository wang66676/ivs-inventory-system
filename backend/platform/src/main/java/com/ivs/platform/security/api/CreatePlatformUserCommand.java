package com.ivs.platform.security.api;

import com.ivs.platform.security.DataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreatePlatformUserCommand(
        @NotBlank(message = "用户名不能为空") @Size(max = 80) String username,
        @NotBlank(message = "姓名不能为空") @Size(max = 120) String displayName,
        @NotBlank(message = "密码不能为空") @Size(min = 8, max = 80, message = "密码长度必须为8到80位") String password,
        DataScope dataScope,
        UUID companyId,
        UUID storeId,
        UUID teamId,
        @NotEmpty(message = "至少选择一个角色") Set<String> roles
) {
}
