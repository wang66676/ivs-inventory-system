package com.ivs.platform.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 审计台账保留策略：仅清理已经成功投递且超过保留期限的 Outbox 事件。 */
@Service
public class AuditRetentionService {
    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int retentionDays;

    public AuditRetentionService(JdbcTemplate jdbc,
                                 @Value("${ivs.audit.cleanup-enabled:true}") boolean enabled,
                                 @Value("${ivs.audit.retention-days:730}") int retentionDays) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.retentionDays = Math.max(180, retentionDays);
    }

    public int retentionDays() {
        return retentionDays;
    }

    @Scheduled(cron = "${ivs.audit.cleanup-cron:0 10 3 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void scheduledCleanup() {
        if (!enabled) return;
        int deleted = cleanupExpired();
        if (deleted > 0) log.info("已清理 {} 条超过 {} 天的已发布审计事件", deleted, retentionDays);
    }

    @Transactional
    public int cleanupExpired() {
        return cleanupBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }

    @Transactional
    public int cleanupBefore(Instant cutoff) {
        return jdbc.update(
                "delete from ivs.outbox_event where status = 'PUBLISHED' and occurred_at < ?",
                Timestamp.from(cutoff));
    }
}