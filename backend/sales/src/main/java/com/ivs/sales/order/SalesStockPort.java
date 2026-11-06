package com.ivs.sales.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 库存端口：销售在确认订单时锁货、出库时扣减库存并取得结转成本。
 * 实现由 app 层适配库存引擎与序列号档案。
 */
public interface SalesStockPort {

    record LineLock(UUID orderLineId, UUID productId, List<UUID> serialIds, BigDecimal quantity) {
    }

    /** 订单确认时锁货（序列号商品按序列号逐台锁定，数量商品按数量锁定）。 */
    void allocate(UUID orderId, String orderNo, UUID warehouseId, List<LineLock> lines);

    /** 取消订单时释放该订单全部有效锁定。 */
    void releaseAll(UUID orderId);

    /** 出库后处理锁定：释放已交付部分的锁定，若仍有剩余则按剩余数量重新锁定。 */
    void settleLocks(UUID orderId, UUID orderLineId, UUID productId, UUID warehouseId,
                     List<UUID> deliveredSerialIds, List<UUID> remainingSerialIds, BigDecimal remainingQuantity);

    /** 出库：扣减库存（写台账与余额），返回本次出库成本合计。 */
    BigDecimal issue(UUID orderId, String orderNo, UUID warehouseId, UUID orderLineId, UUID productId,
                     List<UUID> serialIds, BigDecimal quantity);

    /**
     * 出库前在库量校验：只校验「货是否在库」，不计入本单自身的锁定
     * （锁定占用的是可用量，而本次出库正是在消耗自己锁定的那部分）。
     */
    void requireDeliverable(UUID productId, UUID warehouseId, BigDecimal quantity);

    /** 分期以租代售时标记所有权，结清后转回自有。 */
    void markOwnership(List<UUID> serialIds, boolean leaseToOwn);
}
