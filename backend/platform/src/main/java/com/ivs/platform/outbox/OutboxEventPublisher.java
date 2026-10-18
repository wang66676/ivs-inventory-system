package com.ivs.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventPublisher implements EventPublisher {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(EventEnvelope event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            repository.save(new OutboxEvent(
                    event.eventId(),
                    event.eventType(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.aggregateVersion(),
                    event.tenantId(),
                    event.traceId(),
                    event.occurredAt(),
                    payload
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化领域事件: " + event.eventType(), exception);
        }
    }
}
