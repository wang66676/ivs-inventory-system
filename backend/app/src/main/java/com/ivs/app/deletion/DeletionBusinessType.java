package com.ivs.app.deletion;

public enum DeletionBusinessType {
    SALES_ORDER("整车销售订单"),
    PRE_ENTITLEMENT("预享受项");

    private final String label;

    DeletionBusinessType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
