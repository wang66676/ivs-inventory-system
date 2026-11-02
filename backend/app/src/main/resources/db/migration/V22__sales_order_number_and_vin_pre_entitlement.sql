-- 销售订单增加可手填的业务订单编号；预享受项改为按 VIN/SN + 政策逐条生成。
ALTER TABLE ivs.sales_order ADD COLUMN external_order_no varchar(80);
UPDATE ivs.sales_order SET external_order_no = order_no WHERE external_order_no IS NULL;
CREATE INDEX idx_sales_order_external_no ON ivs.sales_order (external_order_no);

ALTER TABLE ivs.policy_pre_entitlement DROP CONSTRAINT uk_pre_order_line_policy;
CREATE UNIQUE INDEX uk_pre_order_line_serial_policy
    ON ivs.policy_pre_entitlement (order_id, order_line_id, serial_id, policy_id);
