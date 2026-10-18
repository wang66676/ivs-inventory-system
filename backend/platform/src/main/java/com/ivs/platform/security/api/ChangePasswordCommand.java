package com.ivs.platform.security.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改自己的登录密码。 */
public record ChangePasswordCommand(
        @NotBlank(message = "请输入原密码") String currentPassword,
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 80, message = "新密码长度必须为8到80位") String newPassword
) {
}
