package com.ivs.platform.operations;

import com.ivs.platform.approval.NotificationService;
import com.ivs.platform.approval.UserNotificationRepository;
import com.ivs.platform.outbox.OutboxEventRepository;
import com.ivs.platform.outbox.OutboxStatus;
import com.ivs.platform.security.PermissionCodes;
import com.ivs.platform.security.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 运行状态检查、异常告警与管理员站内通知。 */
@Service
public class SystemHealthService {
    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    private final JdbcTemplate jdbc;
    private final PlatformUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventRepository outbox;
    private final UserNotificationRepository notifications;
    private final NotificationService notificationService;
    private final BackupService backups;
    private final AuditRetentionService auditRetention;
    private final String version;
    private final String corsOrigins;
    private final Path invoiceDir;
    private final int maxBackupAgeHours;
    private final long minFreeMb;

    public SystemHealthService(JdbcTemplate jdbc, PlatformUserRepository users, PasswordEncoder passwordEncoder,
                               OutboxEventRepository outbox, UserNotificationRepository notifications,
                               NotificationService notificationService, BackupService backups,
                               AuditRetentionService auditRetention,
                               @Value("${ivs.version:dev}") String version,
                               @Value("${ivs.cors.allowed-origins:}") String corsOrigins,
                               @Value("${ivs.invoice.storage-dir:./data/invoice-files}") String invoiceDir,
                               @Value("${ivs.health.backup-max-age-hours:36}") int maxBackupAgeHours,
                               @Value("${ivs.health.min-free-mb:1024}") long minFreeMb) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.outbox = outbox;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.backups = backups;
        this.auditRetention = auditRetention;
        this.version = version;
        this.corsOrigins = corsOrigins;
        this.invoiceDir = Paths.get(invoiceDir).toAbsolutePath().normalize();
        this.maxBackupAgeHours = Math.max(1, maxBackupAgeHours);
        this.minFreeMb = Math.max(1, minFreeMb);
    }

    public HealthView inspect() {
        List<CheckView> checks = new ArrayList<>();
        checks.add(databaseCheck());
        checks.add(outboxCheck());
        checks.add(backupCheck());
        checks.add(diskCheck());
        checks.add(defaultAdminCheck());
        checks.add(corsCheck());
        checks.add(fileUploadCheck());
        checks.add(auditRetentionCheck());
        String overall = checks.stream().anyMatch(check -> "ERROR".equals(check.status())) ? "ERROR"
                : checks.stream().anyMatch(check -> "WARN".equals(check.status())) ? "WARN" : "OK";
        return new HealthView(overall, version, Instant.now(), checks);
    }

    @Scheduled(initialDelayString = "${ivs.health.initial-delay-ms:60000}",
            fixedDelayString = "${ivs.health.check-interval-ms:900000}")
    public void monitor() {
        HealthView health = inspect();
        if ("OK".equals(health.status())) return;
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        if (notifications.existsByCategoryAndCreatedAtAfter("SYSTEM_HEALTH", since)) return;
        String detail = health.checks().stream().filter(check -> !"OK".equals(check.status()))
                .map(check -> check.label() + "：" + check.detail()).reduce((a, b) -> a + "；" + b).orElse("");
        notificationService.notifyPermission("SYSTEM_HEALTH", "系统健康告警", detail,
                "SYSTEM_HEALTH", null, PermissionCodes.USER_MANAGE);
        log.warn("系统健康告警: {}", detail);
    }

    private CheckView databaseCheck() {
        try {
            Integer value = jdbc.queryForObject("select 1", Integer.class);
            return new CheckView("DATABASE", "数据库连接", value != null && value == 1 ? "OK" : "ERROR",
                    value != null && value == 1 ? "数据库查询正常" : "数据库健康查询返回异常");
        } catch (Exception exception) {
            return new CheckView("DATABASE", "数据库连接", "ERROR", exception.getMessage());
        }
    }

    private CheckView outboxCheck() {
        try {
            long failed = outbox.countByStatusAndCreatedAtAfter(OutboxStatus.FAILED,
                    Instant.now().minus(24, ChronoUnit.HOURS));
            return new CheckView("OUTBOX", "事件投递", failed == 0 ? "OK" : "ERROR",
                    failed == 0 ? "近 24 小时无失败事件" : "近 24 小时有 " + failed + " 条失败事件");
        } catch (Exception exception) {
            return new CheckView("OUTBOX", "事件投递", "ERROR", exception.getMessage());
        }
    }

    private CheckView backupCheck() {
        Optional<Path> latest = backups.latestBackup();
        if (latest.isEmpty()) {
            return new CheckView("BACKUP", "每日备份", "WARN", "尚未生成备份包，请立即执行一次备份");
        }
        try {
            Instant time = Files.getLastModifiedTime(latest.get()).toInstant();
            long ageHours = ChronoUnit.HOURS.between(time, Instant.now());
            String detail = latest.get().getFileName() + "，约 " + Math.max(0, ageHours) + " 小时前";
            return new CheckView("BACKUP", "每日备份", ageHours <= maxBackupAgeHours ? "OK" : "WARN", detail);
        } catch (Exception exception) {
            return new CheckView("BACKUP", "每日备份", "WARN", exception.getMessage());
        }
    }

    private CheckView diskCheck() {
        try {
            Files.createDirectories(backups.backupDirectory());
            long freeMb = Files.getFileStore(backups.backupDirectory()).getUsableSpace() / 1024 / 1024;
            return new CheckView("DISK", "磁盘空间", freeMb >= minFreeMb ? "OK" : "ERROR",
                    "可用 " + freeMb + " MB，最低要求 " + minFreeMb + " MB");
        } catch (Exception exception) {
            return new CheckView("DISK", "磁盘空间", "ERROR", exception.getMessage());
        }
    }

    private CheckView defaultAdminCheck() {
        boolean defaultPassword = users.findByUsername("admin")
                .map(user -> passwordEncoder.matches("admin123", user.getPasswordHash())).orElse(false);
        return new CheckView("DEFAULT_ADMIN", "默认管理员密码", defaultPassword ? "WARN" : "OK",
                defaultPassword ? "admin 仍在使用默认密码，请立即修改" : "未检测到默认管理员密码");
    }

    private CheckView corsCheck() {
        boolean wildcard = corsOrigins == null || List.of(corsOrigins.split(",")).stream()
                .map(String::trim).anyMatch("*"::equals);
        return new CheckView("CORS", "跨域白名单", wildcard ? "ERROR" : "OK",
                wildcard ? "禁止在生产环境使用通配符 CORS" : "已配置固定来源：" + corsOrigins);
    }

    private CheckView fileUploadCheck() {
        try {
            Files.createDirectories(invoiceDir);
            String detail = "PDF 单文件上限 20MB；附件目录：" + invoiceDir;
            return new CheckView("FILE_UPLOAD", "附件上传", Files.isWritable(invoiceDir) ? "OK" : "ERROR", detail);
        } catch (Exception exception) {
            return new CheckView("FILE_UPLOAD", "附件上传", "ERROR", exception.getMessage());
        }
    }

    private CheckView auditRetentionCheck() {
        int days = auditRetention.retentionDays();
        return new CheckView("AUDIT_RETENTION", "审计保留策略", days >= 180 ? "OK" : "WARN",
                "已发布审计事件保留 " + days + " 天");
    }

    public record CheckView(String code, String label, String status, String detail) {
    }

    public record HealthView(String status, String version, Instant checkedAt, List<CheckView> checks) {
    }
}