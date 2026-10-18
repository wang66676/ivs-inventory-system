-- V18：采购明细补齐车系来源、订单编号与返利政策多选
ALTER TABLE ivs.purchase_order ADD COLUMN external_order_no varchar(80);
CREATE INDEX idx_purchase_order_external_no ON ivs.purchase_order (external_order_no);

CREATE TABLE ivs.purchase_order_line_policy (
    id uuid PRIMARY KEY,
    order_line_id uuid NOT NULL,
    policy_id uuid NOT NULL,
    sort_no integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_purchase_order_line_policy UNIQUE (order_line_id, policy_id)
);
CREATE INDEX idx_purchase_order_line_policy_line ON ivs.purchase_order_line_policy (order_line_id, sort_no);
CREATE INDEX idx_purchase_order_line_policy_policy ON ivs.purchase_order_line_policy (policy_id);
