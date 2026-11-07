-- V31：独立客户退款流程与退款审批规则。
CREATE SEQUENCE IF NOT EXISTS ivs.refund_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.refund_order (
    id uuid PRIMARY KEY,
    refund_no varchar(40) NOT NULL,
    customer_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    method varchar(30) NOT NULL,
    source_type varchar(40) NOT NULL,
    source_no varchar(100),
    reason varchar(500) NOT NULL,
    status varchar(30) NOT NULL,
    failure_reason varchar(500),
    execution_remark varchar(500),
    retry_count integer NOT NULL DEFAULT 0,
    manual_resolved boolean NOT NULL DEFAULT false,
    requested_by uuid,
    approved_by uuid,
    approved_at timestamp with time zone,
    executed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_refund_no UNIQUE (refund_no)
);
CREATE INDEX idx_refund_status ON ivs.refund_order (status, created_at);
CREATE INDEX idx_refund_customer ON ivs.refund_order (customer_id, created_at);

INSERT INTO ivs.approval_rule
    (rule_code, rule_name, scenario, enabled, metric_type, threshold_value, approval_permission, description, sort_order, created_at, updated_at, version)
VALUES
    ('REFUND_APPROVAL', '退款审批', 'FUND', true, 'AMOUNT', 0, 'IVS_REFUND_MANAGE',
     '退款申请金额达到阈值时，必须由退款管理员审批通过后才能执行。阈值 0 表示任何退款均需审批。', 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
