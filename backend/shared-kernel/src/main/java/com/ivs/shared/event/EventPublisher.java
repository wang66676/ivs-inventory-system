package com.ivs.shared.event;

/**
 * 领域事件发布端口。平台模块提供可靠实现，业务模块只依赖该接口。
 */
public interface EventPublisher {
    void publish(EventEnvelope event);
}
