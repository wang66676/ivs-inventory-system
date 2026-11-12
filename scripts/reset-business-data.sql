-- IVS 全量业务数据清空（保留 admin、角色权限、审批规则、全局参数和 Flyway 历史）
SET SCHEMA ivs;

-- 审批待办、通知与退款（M5）
DELETE FROM ivs.user_notification_read;
DELETE FROM ivs.user_notification;
DELETE FROM ivs.approval_task;
DELETE FROM ivs.payable_invoice_match;
DELETE FROM ivs.refund_order;

-- 数据迁移与会话事件
DELETE FROM ivs.data_migration_item;
DELETE FROM ivs.data_migration_batch;
DELETE FROM ivs.user_session;
DELETE FROM ivs.outbox_event;
DELETE FROM ivs.inbox_event;

-- 厂家申报、权益、政策、预享受与返利资金池
DELETE FROM ivs.oem_claim_difference;
DELETE FROM ivs.oem_claim_line;
DELETE FROM ivs.oem_claim;
DELETE FROM ivs.policy_cost_allocation;
DELETE FROM ivs.policy_entitlement;
DELETE FROM ivs.purchase_invoice;
DELETE FROM ivs.policy_pre_entitlement;
DELETE FROM ivs.policy_subsidy_pool_usage;
DELETE FROM ivs.policy_subsidy_pool;
DELETE FROM ivs.promotion_policy;
DELETE FROM ivs.retail_reversal;
DELETE FROM ivs.retail_record;
DELETE FROM ivs.rebate_offset_line;
DELETE FROM ivs.rebate_offset_order;
DELETE FROM ivs.rebate_fund_txn;
DELETE FROM ivs.rebate_fund_supplier_balance;
DELETE FROM ivs.rebate_fund_pool;

-- 收款、分期与租赁（含二期）
DELETE FROM ivs.receipt_allocation;
DELETE FROM ivs.receipt;
DELETE FROM ivs.installment_schedule;
DELETE FROM ivs.installment_plan;
DELETE FROM ivs.lease_settlement;
DELETE FROM ivs.lease_deposit_txn;
DELETE FROM ivs.lease_battery_health;
DELETE FROM ivs.lease_depreciation_schedule;
DELETE FROM ivs.lease_contract_change;
DELETE FROM ivs.lease_asset_disposal;
DELETE FROM ivs.lease_reminder_log;
DELETE FROM ivs.lease_schedule;
DELETE FROM ivs.lease_contract;
DELETE FROM ivs.receivable;

-- 销售、退货与发票
DELETE FROM ivs.sales_delivery_line;
DELETE FROM ivs.sales_delivery;
DELETE FROM ivs.sales_order_line;
DELETE FROM ivs.sales_order;
DELETE FROM ivs.sales_invoice;
DELETE FROM ivs.sales_return_line;
DELETE FROM ivs.sales_return;

-- 采购、库存与应付
DELETE FROM ivs.purchase_return_line;
DELETE FROM ivs.purchase_return;
DELETE FROM ivs.stock_transfer_line;
DELETE FROM ivs.stock_transfer;
DELETE FROM ivs.stocktake_line;
DELETE FROM ivs.stocktake;
DELETE FROM ivs.stock_lock;
DELETE FROM ivs.stock_ledger;
DELETE FROM ivs.stock_balance;
DELETE FROM ivs.payable;
DELETE FROM ivs.purchase_receipt_line;
DELETE FROM ivs.purchase_receipt;
DELETE FROM ivs.purchase_order_line_policy;
DELETE FROM ivs.purchase_order_line;
DELETE FROM ivs.purchase_order;

-- 主数据
DELETE FROM ivs.price_list_line;
DELETE FROM ivs.price_list;
DELETE FROM ivs.product_serial;
DELETE FROM ivs.product_attr_def;
DELETE FROM ivs.product;
DELETE FROM ivs.product_category;
DELETE FROM ivs.warehouse;
DELETE FROM ivs.supplier;
DELETE FROM ivs.customer;

-- 保留 admin、角色、角色权限和审批规则；清理测试组织及其他账号
DELETE FROM ivs.platform_user_role WHERE user_id NOT IN (SELECT id FROM ivs.platform_user WHERE username = 'admin');
DELETE FROM ivs.platform_user WHERE username <> 'admin';
DELETE FROM ivs.company;
DELETE FROM ivs.store;
DELETE FROM ivs.team;
UPDATE ivs.platform_user
SET company_id = NULL, store_id = NULL, team_id = NULL, data_scope = 'ALL'
WHERE username = 'admin';

-- 业务编号从 1 重新开始
ALTER SEQUENCE ivs.product_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.supplier_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.customer_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.price_list_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.purchase_order_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.purchase_receipt_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.receipt_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.receivable_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_order_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_delivery_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_invoice_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.rebate_offset_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.retail_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.policy_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.pre_entitlement_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.purchase_invoice_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.entitlement_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.claim_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.stocktake_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.stock_transfer_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.purchase_return_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_return_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.data_migration_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.payable_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.installment_plan_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.refund_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.approval_task_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.lease_contract_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.lease_disposal_no_seq RESTART WITH 1;
