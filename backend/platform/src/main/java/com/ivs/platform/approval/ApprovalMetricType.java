package com.ivs.platform.approval;

/** 审批触发指标的计量方式。 */
public enum ApprovalMetricType {
    ALWAYS("始终需要"),
    NONE("无需审批"),
    AMOUNT("金额达到阈值"),
    DISCOUNT_PERCENT("折让比例达到阈值");

    private final String label;

    ApprovalMetricType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
