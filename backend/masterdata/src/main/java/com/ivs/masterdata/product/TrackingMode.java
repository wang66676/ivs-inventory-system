package com.ivs.masterdata.product;

/**
 * 库存追踪方式：数量管理（配件等）或序列号管理（整车 VIN、电池 SN）。
 * 产品大项用该字段切换库存引擎，新增产品大项属于配置动作。
 */
public enum TrackingMode {
    QUANTITY,
    SERIAL
}