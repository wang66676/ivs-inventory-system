package com.ivs.platform.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxFailureRecorder {
    private final OutboxEventRepository repository;

    public OutboxFailureRecorder(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID outboxEventId, Exception exception) {
        repository.findById(outboxEventId).ifPresent(event ->
                event.markFailed(Instant.now(), exception.getMessage()));
    }
}
