-- IVS 选择性清空销售、实销、兑现申请、申报及返利流水
SET SCHEMA ivs;

-- 1. 恢复销售出库所占库存，并清除销售锁库
UPDATE ivs.stock_balance b
SET on_hand_qty = b.on_hand_qty + COALESCE((
        SELECT SUM(CASE WHEN l.move_type = 'SALE_OUT' THEN l.quantity
                        WHEN l.move_type = 'SALE_RETURN_IN' THEN -l.quantity
                        ELSE 0 END)
        FROM ivs.stock_ledger l
        WHERE l.product_id = b.product_id AND l.warehouse_id = b.warehouse_id
          AND l.move_type IN ('SALE_OUT','SALE_RETURN_IN','EXCHANGE_OUT')
    ), 0),
    amount = b.amount + COALESCE((
        SELECT SUM(CASE WHEN l.move_type = 'SALE_OUT' THEN l.amount
                        WHEN l.move_type = 'SALE_RETURN_IN' THEN -l.amount
                        ELSE 0 END)
        FROM ivs.stock_ledger l
        WHERE l.product_id = b.product_id AND l.warehouse_id = b.warehouse_id
          AND l.move_type IN ('SALE_OUT','SALE_RETURN_IN','EXCHANGE_OUT')
    ), 0),
    locked_qty = 0;

UPDATE ivs.stock_balance
SET available_qty = CASE WHEN on_hand_qty > 0 THEN on_hand_qty ELSE 0 END;

DELETE FROM ivs.stock_ledger
WHERE move_type IN ('SALE_OUT','SALE_RETURN_IN','EXCHANGE_OUT');

UPDATE ivs.product_serial
SET status = 'IN_STOCK'
WHERE status IN ('LOCKED','SOLD','DELIVERED');

DELETE FROM ivs.stock_lock;

-- 2. 清空申报、权益、预享受、政策成本分摊和额度池占用
DELETE FROM ivs.oem_claim_difference;
DELETE FROM ivs.oem_claim_line;
DELETE FROM ivs.oem_claim;
DELETE FROM ivs.policy_cost_allocation;
DELETE FROM ivs.policy_entitlement;
DELETE FROM ivs.policy_pre_entitlement;
DELETE FROM ivs.policy_subsidy_pool_usage;
UPDATE ivs.policy_subsidy_pool SET used_amount = 0, status = CASE WHEN status = 'EXHAUSTED' THEN 'ACTIVE' ELSE status END;
DELETE FROM ivs.purchase_order_line_policy;

-- 3. 清除政策红字发票及返利资金池/抵扣流水
DELETE FROM ivs.purchase_invoice_rebate_line;
DELETE FROM ivs.purchase_invoice WHERE type = 'RED';
UPDATE ivs.payable SET rebate_credit_amount = 0;
UPDATE ivs.purchase_receipt SET offset_amount = 0;
DELETE FROM ivs.rebate_offset_line;
DELETE FROM ivs.rebate_offset_order;
DELETE FROM ivs.rebate_fund_txn;
DELETE FROM ivs.rebate_fund_supplier_balance;
DELETE FROM ivs.rebate_fund_pool;

-- 4. 清空销售、收款、应收、退款和实销
DELETE FROM ivs.receipt_allocation;
DELETE FROM ivs.receipt;
DELETE FROM ivs.installment_schedule;
DELETE FROM ivs.installment_plan;
DELETE FROM ivs.receivable;
DELETE FROM ivs.sales_invoice;
DELETE FROM ivs.sales_return_line;
DELETE FROM ivs.sales_return;
DELETE FROM ivs.sales_delivery_line;
DELETE FROM ivs.sales_delivery;
DELETE FROM ivs.sales_order_line;
DELETE FROM ivs.sales_order;
DELETE FROM ivs.refund_order;
DELETE FROM ivs.retail_reversal;
DELETE FROM ivs.retail_record;

-- 5. 清空由上述业务产生的待办和通知
DELETE FROM ivs.user_notification_read;
DELETE FROM ivs.user_notification;
DELETE FROM ivs.approval_task;

-- 6. 重置相关编号序列（采购和蓝字进项发票编号保持连续）
ALTER SEQUENCE ivs.sales_order_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_delivery_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_invoice_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.sales_return_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.receipt_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.receivable_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.installment_plan_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.refund_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.retail_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.pre_entitlement_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.entitlement_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.claim_no_seq RESTART WITH 1;
ALTER SEQUENCE ivs.rebate_offset_no_seq RESTART WITH 1;
