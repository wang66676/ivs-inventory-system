package com.ivs.platform.security.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员为指定用户重置登录密码。 */
public record ResetPasswordCommand(
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 80, message = "新密码长度必须为8到80位") String password
) {
}
