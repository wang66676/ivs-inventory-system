package com.ivs.platform.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ivs.outbox", name = "dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final OutboxDispatcher dispatcher;

    public OutboxScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${ivs.outbox.dispatch-interval-ms:3000}")
    public void dispatch() {
        dispatcher.dispatchPendingEvents();
    }
}
