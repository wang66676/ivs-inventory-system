-- V11：申报单、申报差异与政策成本分摊（M4-3b）
-- 政策绑定 OEM 厂家；权益记录供应商便于申报归集与报表。
ALTER TABLE ivs.promotion_policy ADD COLUMN supplier_id uuid;
CREATE INDEX idx_policy_supplier ON ivs.promotion_policy (supplier_id, status);

ALTER TABLE ivs.policy_entitlement ADD COLUMN supplier_id uuid;
CREATE INDEX idx_entitlement_supplier ON ivs.policy_entitlement (supplier_id, status);

CREATE SEQUENCE IF NOT EXISTS ivs.claim_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.oem_claim (
    id uuid PRIMARY KEY,
    claim_no varchar(40) NOT NULL,
    supplier_id uuid NOT NULL,
    policy_id uuid,
    period_code varchar(20) NOT NULL,
    settle_cycle varchar(20) NOT NULL,
    merged_claim boolean NOT NULL DEFAULT false,
    claim_amount numeric(18,2) NOT NULL DEFAULT 0,
    confirmed_amount numeric(18,2) NOT NULL DEFAULT 0,
    difference_amount numeric(18,2) NOT NULL DEFAULT 0,
    cutoff_at timestamp with time zone,
    status varchar(30) NOT NULL,
    submitted_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    settled_at timestamp with time zone,
    closed_at timestamp with time zone,
    invoice_id uuid,
    invoice_no varchar(100),
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_oem_claim_no UNIQUE (claim_no)
);
CREATE INDEX idx_oem_claim_group ON ivs.oem_claim (supplier_id, period_code, status);
CREATE INDEX idx_oem_claim_policy ON ivs.oem_claim (policy_id, period_code);

CREATE TABLE ivs.oem_claim_line (
    id uuid PRIMARY KEY,
    claim_id uuid NOT NULL,
    entitlement_id uuid NOT NULL,
    retail_id uuid,
    policy_id uuid,
    serial_id uuid,
    amount numeric(18,2) NOT NULL,
    confirmed_amount numeric(18,2) NOT NULL DEFAULT 0,
    difference_amount numeric(18,2) NOT NULL DEFAULT 0,
    difference_reason varchar(300),
    difference_disposition varchar(30),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_claim_line_entitlement UNIQUE (entitlement_id)
);
CREATE INDEX idx_claim_line_claim ON ivs.oem_claim_line (claim_id);

CREATE TABLE ivs.oem_claim_difference (
    id uuid PRIMARY KEY,
    claim_id uuid NOT NULL,
    claim_line_id uuid NOT NULL,
    entitlement_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    reason varchar(300) NOT NULL,
    disposition varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    resolution_remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone
);
CREATE INDEX idx_claim_difference_claim ON ivs.oem_claim_difference (claim_id, status);

CREATE TABLE ivs.policy_cost_allocation (
    id uuid PRIMARY KEY,
    entitlement_id uuid NOT NULL,
    order_id uuid,
    retail_id uuid,
    serial_id uuid,
    policy_id uuid NOT NULL,
    cost_bearer varchar(20) NOT NULL,
    cost_subject varchar(40),
    amount numeric(18,2) NOT NULL,
    period_code varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_policy_cost_entitlement UNIQUE (entitlement_id)
);
CREATE INDEX idx_policy_cost_period ON ivs.policy_cost_allocation (period_code, policy_id);
