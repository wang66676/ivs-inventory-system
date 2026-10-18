package com.ivs.retail.retail;

/** 实销状态：草稿 → 已提交 → 已复核（政策兑现基准）→ 已冲正。 */
public enum RetailStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED,
    REVERSED
}