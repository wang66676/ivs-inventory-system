package com.ivs.sales.order;

import java.util.UUID;

/** 销售订单删除后的下游数据清理端口，由 app 层实现，避免销售模块直接依赖票据、资金和实销模块。 */
public interface SalesOrderHistoryCleanupPort {
    void deleteDownstream(UUID orderId);
}