package com.ivs.platform.inbox;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InboxService {
    private final InboxEventRepository repository;

    public InboxService(InboxEventRepository repository) {
        this.repository = repository;
    }

    /**
     * 幂等消费入口。返回 true 表示首次消费，false 表示已经处理过。
     */
    @Transactional
    public boolean consume(String consumer, UUID eventId) {
        if (repository.existsByConsumerAndEventId(consumer, eventId)) {
            return false;
        }
        try {
            repository.saveAndFlush(new InboxEvent(consumer, eventId, Instant.now()));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
