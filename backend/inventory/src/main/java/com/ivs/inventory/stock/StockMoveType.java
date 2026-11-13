package com.ivs.inventory.stock;

/** 库存变动类型（对应《落地方案 v1.0》第 4.3 节）。 */
public enum StockMoveType {
    OPENING,
    PURCHASE_IN,
    SALE_OUT,
    TRANSFER_OUT,
    TRANSFER_IN,
    STOCKTAKE_ADJUST,
    MANUAL_ADJUST,
    SALE_RETURN_IN,
    PURCHASE_RETURN_OUT,
    PURCHASE_RECEIPT_REVERSAL,
    EXCHANGE_OUT,
    SCRAP,
    LEASE_OUT,
    LEASE_RETURN,
    LEASE_RETIRE
}
