package com.ivs.masterdata;

/** 序列号档案状态（整车 VIN / 电池 SN 一物一档）。 */
public enum SerialStatus {
    PLANNED,
    IN_TRANSIT,
    ARRIVED,
    IN_STOCK,
    LOCKED,
    SOLD,
    DELIVERED,
    RETURNED,
    REPAIRING,
    IN_LEASE,
    AT_STATION,
    IN_USE,
    CHARGING,
    RETIRED,
    SCRAPPED
}
