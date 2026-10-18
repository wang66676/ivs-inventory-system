package com.ivs.sales.order;

/** 销售订单状态。 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_DELIVERED,
    DELIVERED,
    CLOSED,
    CANCELLED
}