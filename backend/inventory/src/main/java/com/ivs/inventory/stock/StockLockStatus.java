package com.ivs.inventory.stock;

/** 库存锁定状态。 */
public enum StockLockStatus {
    ACTIVE,
    RELEASED,
    CONSUMED,
    EXPIRED
}