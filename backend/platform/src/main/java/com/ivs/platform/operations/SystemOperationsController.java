package com.ivs.platform.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员系统健康检查和手工在线备份入口。 */
@RestController
@RequestMapping("/api/platform/system-health")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class SystemOperationsController {
    private final SystemHealthService health;
    private final BackupService backups;

    public SystemOperationsController(SystemHealthService health, BackupService backups) {
        this.health = health;
        this.backups = backups;
    }

    @GetMapping
    public SystemHealthService.HealthView status() {
        return health.inspect();
    }

    @PostMapping("/backup")
    public BackupService.BackupResult backup() {
        return backups.createBackup();
    }
}