package com.ivs.platform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository repository;
    private final OutboxEventProcessor processor;
    private final OutboxFailureRecorder failureRecorder;

    public OutboxDispatcher(OutboxEventRepository repository,
                            OutboxEventProcessor processor,
                            OutboxFailureRecorder failureRecorder) {
        this.repository = repository;
        this.processor = processor;
        this.failureRecorder = failureRecorder;
    }

    /**
     * 逐个处理待投递事件。每个事件使用独立事务，一个事件失败不会回滚其他事件。
     */
    public int dispatchPendingEvents() {
        List<UUID> pendingIds = repository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, java.time.Instant.now())
                .stream()
                .map(OutboxEvent::getId)
                .toList();

        int processed = 0;
        for (UUID eventId : pendingIds) {
            try {
                processor.process(eventId);
                processed++;
            } catch (Exception exception) {
                log.error("Outbox event processing failed: {}", eventId, exception);
                failureRecorder.recordFailure(eventId, exception);
            }
        }
        return processed;
    }
}
