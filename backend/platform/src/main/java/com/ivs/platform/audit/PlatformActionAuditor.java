package com.ivs.platform.audit;

import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 平台动作审计：把「谁在什么时候做了什么」写入平台事件台账（Outbox），
 * 由审计日志接口统一检索。业务模块的领域事件同样落入该台账，
 * 因此审计检索天然覆盖平台动作与业务动作。
 */
@Component
public class PlatformActionAuditor {

    private final EventPublisher events;
    private final CurrentActorProvider actors;

    public PlatformActionAuditor(EventPublisher events, CurrentActorProvider actors) {
        this.events = events;
        this.actors = actors;
    }

    public void record(String eventType, String aggregateType, UUID aggregateId) {
        record(eventType, aggregateType, aggregateId, Map.of());
    }

    public void record(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (value != null) {
                    data.put(key, value);
                }
            });
        }
        actors.current().ifPresent(actor -> {
            data.putIfAbsent("operator", actor.username());
            if (actor.userId() != null) {
                data.putIfAbsent("operatorId", actor.userId().toString());
            }
        });
        events.publish(new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId == null ? UUID.randomUUID() : aggregateId,
                0L,
                null,
                UUID.randomUUID(),
                Instant.now(),
                data
        ));
    }
}