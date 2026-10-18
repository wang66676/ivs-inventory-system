package com.ivs.billing.invoice;

/** 发票状态：草稿 → 已开具 → 作废 / 红冲。 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    VOID,
    RED_FLUSHED
}