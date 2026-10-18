package com.ivs.platform.audit;
import java.time.Instant;import java.util.UUID;
public record AuditLogEntry(String source,UUID eventId,UUID processId,String eventType,String aggregateType,UUID aggregateId,String status,Integer attempts,String message,UUID traceId,Instant occurredAt) {}
