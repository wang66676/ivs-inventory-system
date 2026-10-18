package com.ivs.policy.policy;

/** 政策状态：草稿 → 已发布 → 已取消；到期由日期区间控制。 */
public enum PolicyStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}