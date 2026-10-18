package com.ivs.platform.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_event", schema = "ivs",
        uniqueConstraints = @UniqueConstraint(name = "uk_inbox_consumer_event", columnNames = {"consumer", "event_id"}))
public class InboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String consumer;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected InboxEvent() {
    }

    public InboxEvent(String consumer, UUID eventId, Instant processedAt) {
        this.id = UUID.randomUUID();
        this.consumer = consumer;
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }
}


