package com.ivs.app;

import com.ivs.platform.operations.AuditRetentionService;
import com.ivs.platform.operations.BackupService;
import com.ivs.platform.operations.SystemHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 上线前运维收口：在线备份、恢复验证、健康检查与审计保留。 */
@SpringBootTest(properties = {
        "ivs.backup.dir=target/test-backups",
        "ivs.invoice.storage-dir=target/test-invoice-files",
        "ivs.health.initial-delay-ms=3600000",
        "ivs.health.check-interval-ms=3600000",
        "ivs.backup.cron=0 0 0 1 1 *"
})
@ActiveProfiles("test")
class SystemOperationsIntegrationTest {

    @Autowired private BackupService backupService;
    @Autowired private SystemHealthService healthService;
    @Autowired private AuditRetentionService auditRetentionService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void 在线备份生成恢复验证包并纳入健康检查() throws Exception {
        BackupService.BackupResult result = backupService.createBackup();
        Path file = Path.of(result.path());
        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(result.sizeBytes()).isGreaterThan(0);
        assertThat(result.restoreVerified()).isTrue();
        assertThat(result.fileName()).startsWith("ivs-backup-").endsWith(".zip");

        SystemHealthService.HealthView health = healthService.inspect();
        assertThat(health.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("DATABASE");
            assertThat(check.status()).isEqualTo("OK");
        });
        assertThat(health.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("BACKUP");
            assertThat(check.status()).isEqualTo("OK");
        });
        Files.deleteIfExists(file);
    }

    @Test
    void 审计保留策略只清理超过期限的已发布事件() {
        UUID oldId = UUID.randomUUID();
        UUID recentId = UUID.randomUUID();
        Instant now = Instant.now();
        insertEvent(oldId, now.minus(400, ChronoUnit.DAYS));
        insertEvent(recentId, now.minus(1, ChronoUnit.DAYS));

        int deleted = auditRetentionService.cleanupBefore(now.minus(365, ChronoUnit.DAYS));

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(count(oldId)).isZero();
        assertThat(count(recentId)).isEqualTo(1);
    }

    private void insertEvent(UUID id, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(occurredAt);
        jdbc.update("""
                insert into ivs.outbox_event
                (id,event_id,event_type,aggregate_type,aggregate_id,aggregate_version,trace_id,occurred_at,
                 payload_json,status,attempts,next_attempt_at,created_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, eventId, "TestAuditEvent", "TestAggregate", aggregateId, 0L, traceId, timestamp,
                "{}", "PUBLISHED", 1, timestamp, timestamp);
    }

    private int count(UUID id) {
        return jdbc.queryForObject("select count(*) from ivs.outbox_event where id = ?", Integer.class, id);
    }
}