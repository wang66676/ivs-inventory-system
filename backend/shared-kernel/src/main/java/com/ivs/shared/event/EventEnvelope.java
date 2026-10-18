package com.ivs.shared.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 跨模块领域事件信封。事件只表达已经发生的事实，并携带追踪和版本信息。
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String tenantId,
        UUID traceId,
        Instant occurredAt,
        Map<String, Object> payload
) {
    public EventEnvelope {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        // 统一过滤 null：事件信封最终用 Map.copyOf，null 值会直接抛 NPE 变成 500。
        // 在入口处过滤，避免每个发布点各写一遍。
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (key != null && value != null) {
                    filtered.put(key, value);
                }
            });
        }
        payload = Map.copyOf(filtered);
    }
}