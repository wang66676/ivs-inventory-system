package com.ivs.platform.setting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 全局参数：仅管理员可访问与修改。 */
@RestController
@RequestMapping("/api/ivs/system-settings")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class SystemSettingController {
    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) { this.service = service; }

    @GetMapping
    public List<SystemSettingService.SettingView> list() { return service.list(); }

    @PutMapping("/{key}")
    public SystemSettingService.SettingView update(@PathVariable("key") String key,
                                                   @Valid @RequestBody UpdateCommand command) {
        return service.update(key, command.value());
    }

    public record UpdateCommand(@NotBlank String value) {}
}
