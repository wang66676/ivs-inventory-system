package com.ivs.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", schema = "ivs")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "trace_id", nullable = false)
    private UUID traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String eventType, String aggregateType, UUID aggregateId,
                       long aggregateVersion, String tenantId, UUID traceId, Instant occurredAt,
                       String payloadJson) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
        this.payloadJson = payloadJson;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = occurredAt;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = occurredAt;
        }
    }

    public void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
        this.lastError = null;
    }

    public void markFailed(Instant now, String error) {
        this.attempts++;
        this.status = attempts >= 5 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        this.nextAttemptAt = now.plusSeconds(Math.min(300L, (long) Math.pow(2, attempts)));
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }
}


