package com.ivs.sales.order;

/** 出库单状态：POSTED 表示库存已出、成本已结转、应收已生成。 */
public enum DeliveryStatus {
    POSTED,
    CANCELLED
}