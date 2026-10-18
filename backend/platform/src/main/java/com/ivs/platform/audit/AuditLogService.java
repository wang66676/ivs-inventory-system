package com.ivs.platform.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 审计检索：读取平台 Outbox 事件台账（已发生事实的完整留痕）。
 * 后续业务模块接入后，可扩展为「业务动作审计」的联合视图。
 */
@Service
public class AuditLogService {

    private final JdbcTemplate jdbc;

    public AuditLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> search(String keyword, String aggregateType, UUID aggregateId,
                                      Instant from, Instant to, int limit) {
        int max = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder(
                "select event_id, event_type, aggregate_type, aggregate_id, status, attempts, trace_id, occurred_at "
                        + "from ivs.outbox_event where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and lower(event_type) like ?");
            params.add("%" + keyword.trim().toLowerCase() + "%");
        }
        if (aggregateType != null && !aggregateType.isBlank()) {
            sql.append(" and lower(aggregate_type) = lower(?)");
            params.add(aggregateType.trim());
        }
        if (aggregateId != null) {
            sql.append(" and aggregate_id = ?");
            params.add(aggregateId);
        }
        if (from != null) {
            sql.append(" and occurred_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and occurred_at <= ?");
            params.add(Timestamp.from(to));
        }
        sql.append(" order by occurred_at desc limit ?");
        params.add(max);
        return jdbc.query(sql.toString(), (rs, n) -> new AuditLogEntry(
                "OUTBOX",
                rs.getObject(1, UUID.class),
                null,
                rs.getString(2),
                rs.getString(3),
                rs.getObject(4, UUID.class),
                rs.getString(5),
                rs.getInt(6),
                null,
                rs.getObject(7, UUID.class),
                rs.getTimestamp(8).toInstant()
        ), params.toArray());
    }
}