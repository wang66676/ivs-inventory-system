package com.ivs.inventory.stock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 台账与余额对账：余额可由台账重建，两者必须一致。
 *
 * <p>不一致时说明存在绕过库存引擎的直接写入，需要阻断相关单据并排查。</p>
 */
@Service
public class StockReconciliationService {

    private final JdbcTemplate jdbc;

    public StockReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Difference(UUID productId, UUID warehouseId, String batchNo,
                             BigDecimal balanceQty, BigDecimal ledgerQty) {
    }

    /** 返回所有「余额与台账不一致」的条目。 */
    @Transactional(readOnly = true)
    public List<Difference> reconcile() {
        return jdbc.query("""
                select b.product_id, b.warehouse_id, b.batch_no, b.on_hand_qty,
                       coalesce((select sum(case when l.direction = 'IN' then l.quantity else -l.quantity end)
                                 from ivs.stock_ledger l
                                 where l.product_id = b.product_id and l.warehouse_id = b.warehouse_id), 0) as ledger_qty
                  from ivs.stock_balance b
                """, (rs, rowNum) -> new Difference(
                rs.getObject("product_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getString("batch_no"),
                rs.getBigDecimal("on_hand_qty"),
                rs.getBigDecimal("ledger_qty")))
                .stream()
                .filter(item -> item.balanceQty().compareTo(item.ledgerQty()) != 0)
                .toList();
    }

    /** 台账变动总数，用于看板展示。 */
    @Transactional(readOnly = true)
    public long ledgerCount() {
        Long count = jdbc.queryForObject("select count(*) from ivs.stock_ledger", Long.class);
        return count == null ? 0L : count;
    }
}