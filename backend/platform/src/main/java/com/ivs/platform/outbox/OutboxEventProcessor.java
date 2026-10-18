package com.ivs.platform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.shared.event.EventEnvelope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxEventProcessor {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OutboxEventProcessor(OutboxEventRepository repository,
                                ObjectMapper objectMapper,
                                ApplicationEventPublisher applicationEventPublisher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID outboxEventId) throws Exception {
        OutboxEvent outboxEvent = repository.findById(outboxEventId).orElse(null);
        if (outboxEvent == null || outboxEvent.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        EventEnvelope envelope = objectMapper.readValue(outboxEvent.getPayloadJson(), EventEnvelope.class);
        applicationEventPublisher.publishEvent(envelope);
        outboxEvent.markPublished(Instant.now());
    }
}
