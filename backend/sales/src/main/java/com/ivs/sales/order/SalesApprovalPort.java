package com.ivs.sales.order;

import java.math.BigDecimal;

/** 销售审批判定端口：实现放在 app 层，避免销售模块依赖平台审批实体。 */
public interface SalesApprovalPort {
    boolean lowPriceRequiresApproval(BigDecimal salePrice, BigDecimal minPrice);
}
