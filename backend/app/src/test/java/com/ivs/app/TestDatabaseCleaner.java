package com.ivs.app;

import org.springframework.jdbc.core.JdbcTemplate;

/** 用例间清理：保留引导管理员，仅清空会话与事件台账。 */
final class TestDatabaseCleaner {
    private TestDatabaseCleaner() {
    }

    static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("delete from ivs.user_notification_read");
        jdbcTemplate.execute("delete from ivs.user_notification");
        jdbcTemplate.execute("delete from ivs.approval_task");
        jdbcTemplate.execute("delete from ivs.payable_invoice_match");
        jdbcTemplate.execute("delete from ivs.refund_order");
        jdbcTemplate.execute("delete from ivs.data_migration_item");
        jdbcTemplate.execute("delete from ivs.data_migration_batch");
        jdbcTemplate.execute("delete from ivs.user_session");
        jdbcTemplate.execute("delete from ivs.outbox_event");
        jdbcTemplate.execute("delete from ivs.inbox_event");
        jdbcTemplate.execute("delete from ivs.oem_claim_difference");
        jdbcTemplate.execute("delete from ivs.oem_claim_line");
        jdbcTemplate.execute("delete from ivs.oem_claim");
        jdbcTemplate.execute("delete from ivs.policy_cost_allocation");
        jdbcTemplate.execute("delete from ivs.policy_entitlement");
        // 进项红字发票（M4）
        jdbcTemplate.execute("delete from ivs.purchase_invoice");
        // 政策与预享受项（M4）
        jdbcTemplate.execute("delete from ivs.policy_pre_entitlement");
        jdbcTemplate.execute("delete from ivs.policy_subsidy_pool_usage");
        jdbcTemplate.execute("delete from ivs.policy_subsidy_pool");
        jdbcTemplate.execute("delete from ivs.promotion_policy");
        // 实销（M4）
        jdbcTemplate.execute("delete from ivs.retail_reversal");
        jdbcTemplate.execute("delete from ivs.retail_record");
        // 返利资金池（M4）
        jdbcTemplate.execute("delete from ivs.rebate_offset_line");
        jdbcTemplate.execute("delete from ivs.rebate_offset_order");
        jdbcTemplate.execute("delete from ivs.rebate_fund_txn");
        jdbcTemplate.execute("delete from ivs.rebate_fund_supplier_balance");
        jdbcTemplate.execute("delete from ivs.rebate_fund_pool");
        // 销售与结算（M3）
        jdbcTemplate.execute("delete from ivs.receipt_allocation");
        jdbcTemplate.execute("delete from ivs.receipt");
        jdbcTemplate.execute("delete from ivs.installment_schedule");
        jdbcTemplate.execute("delete from ivs.installment_plan");
        jdbcTemplate.execute("delete from ivs.lease_settlement");
        jdbcTemplate.execute("delete from ivs.lease_deposit_txn");
        jdbcTemplate.execute("delete from ivs.lease_battery_health");
        jdbcTemplate.execute("delete from ivs.lease_depreciation_schedule");
        jdbcTemplate.execute("delete from ivs.lease_contract_change");
        jdbcTemplate.execute("delete from ivs.lease_asset_disposal");
        jdbcTemplate.execute("delete from ivs.lease_reminder_log");
        jdbcTemplate.execute("delete from ivs.lease_schedule");
        jdbcTemplate.execute("delete from ivs.lease_contract");
        jdbcTemplate.execute("delete from ivs.receivable");
        jdbcTemplate.execute("delete from ivs.sales_delivery_line");
        jdbcTemplate.execute("delete from ivs.sales_delivery");
        jdbcTemplate.execute("delete from ivs.sales_order_line");
        jdbcTemplate.execute("delete from ivs.sales_order");
        jdbcTemplate.execute("delete from ivs.sales_invoice");
        // 采购与库存（M2）
        jdbcTemplate.execute("delete from ivs.sales_return_line");
        jdbcTemplate.execute("delete from ivs.sales_return");
        jdbcTemplate.execute("delete from ivs.purchase_return_line");
        jdbcTemplate.execute("delete from ivs.purchase_return");
        jdbcTemplate.execute("delete from ivs.stock_transfer_line");
        jdbcTemplate.execute("delete from ivs.stock_transfer");
        jdbcTemplate.execute("delete from ivs.stocktake_line");
        jdbcTemplate.execute("delete from ivs.stocktake");
        jdbcTemplate.execute("delete from ivs.stock_lock");
        jdbcTemplate.execute("delete from ivs.stock_ledger");
        jdbcTemplate.execute("delete from ivs.stock_balance");
        jdbcTemplate.execute("delete from ivs.payable");
        jdbcTemplate.execute("delete from ivs.purchase_receipt_line");
        jdbcTemplate.execute("delete from ivs.purchase_receipt");
        jdbcTemplate.execute("delete from ivs.purchase_order_line_policy");
        jdbcTemplate.execute("delete from ivs.purchase_order_line");
        jdbcTemplate.execute("delete from ivs.purchase_order");
        // 主数据（M1）
        jdbcTemplate.execute("delete from ivs.price_list_line");
        jdbcTemplate.execute("delete from ivs.price_list");
        jdbcTemplate.execute("delete from ivs.product_serial");
        jdbcTemplate.execute("delete from ivs.product_attr_def");
        jdbcTemplate.execute("delete from ivs.product");
        jdbcTemplate.execute("delete from ivs.product_category");
        jdbcTemplate.execute("delete from ivs.warehouse");
        jdbcTemplate.execute("delete from ivs.supplier");
        jdbcTemplate.execute("delete from ivs.customer");
    }
}






