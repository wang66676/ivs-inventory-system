package com.ivs.policy.policy;

/** 额度池限制维度：时间 / 金额 / 条件 / 金额+条件。 */
public enum LimitType {
    TIME_ONLY,
    AMOUNT,
    CONDITION,
    AMOUNT_AND_CONDITION
}