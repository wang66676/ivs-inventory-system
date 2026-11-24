package com.ivs.app.orchestration;

import com.ivs.sales.order.SalesOrderHistoryCleanupPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 销售订单删除时清理所有已不成立的下游业务数据。 */
@Component
public class SalesOrderHistoryCleanupAdapter implements SalesOrderHistoryCleanupPort {

    private final JdbcTemplate jdbc;

    public SalesOrderHistoryCleanupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void deleteDownstream(UUID orderId) {
        jdbc.update("""
                delete from ivs.retail_reversal
                 where retail_id in (select id from ivs.retail_record where order_id = ?)
                """, orderId);
        jdbc.update("delete from ivs.retail_record where order_id = ?", orderId);
        jdbc.update("""
                delete from ivs.receipt_allocation
                 where receivable_id in (
                    select id from ivs.receivable
                     where order_id = ?
                        or delivery_id in (select id from ivs.sales_delivery where order_id = ?)
                 )
                """, orderId, orderId);
        jdbc.update("""
                delete from ivs.receivable
                 where order_id = ?
                    or delivery_id in (select id from ivs.sales_delivery where order_id = ?)
                """, orderId, orderId);
        jdbc.update("""
                delete from ivs.sales_invoice
                 where order_id = ?
                    or delivery_id in (select id from ivs.sales_delivery where order_id = ?)
                """, orderId, orderId);
        jdbc.update("""
                delete from ivs.sales_return_line
                 where return_id in (
                    select id from ivs.sales_return
                     where delivery_id in (select id from ivs.sales_delivery where order_id = ?)
                 )
                """, orderId);
        jdbc.update("""
                delete from ivs.sales_return
                 where delivery_id in (select id from ivs.sales_delivery where order_id = ?)
                """, orderId);
        jdbc.update("""
                delete from ivs.sales_delivery_line
                 where delivery_id in (select id from ivs.sales_delivery where order_id = ?)
                """, orderId);
        jdbc.update("delete from ivs.sales_delivery where order_id = ?", orderId);
        jdbc.update("""
                delete from ivs.installment_schedule
                 where plan_id in (select id from ivs.installment_plan where order_id = ?)
                """, orderId);
        jdbc.update("delete from ivs.installment_plan where order_id = ?", orderId);
        jdbc.update("""
                delete from ivs.user_notification_read
                 where notification_id in (select id from ivs.user_notification where business_id = ?)
                """, orderId);
        jdbc.update("delete from ivs.user_notification where business_id = ?", orderId);
        jdbc.update("delete from ivs.approval_task where business_id = ?", orderId);
        jdbc.update("delete from ivs.stock_lock where biz_id = ?", orderId);
    }
}