package com.ivs.platform.license;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录前可访问的授权状态与离线注册入口。 */
@RestController
@RequestMapping("/api/platform/license")
public class LicenseController {
    private final LicenseService service;

    public LicenseController(LicenseService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public LicenseService.LicenseStatusView status() {
        return service.status();
    }

    @PostMapping("/activate")
    public LicenseService.LicenseStatusView activate(@Valid @RequestBody ActivationCommand command) {
        return service.activate(command.activationCode());
    }

    public record ActivationCommand(@NotBlank String activationCode) {
    }
}
