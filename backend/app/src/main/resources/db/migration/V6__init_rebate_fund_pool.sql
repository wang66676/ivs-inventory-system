-- V6：返利资金池（一个池、按厂家分账）与入库单抵扣
-- 规则：只进「已到账」返利；只能抵扣同一厂家车款；余额分批使用；不允许预抵扣；无需审批；全程留痕。

CREATE SEQUENCE IF NOT EXISTS ivs.rebate_offset_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.rebate_fund_pool (
    id uuid PRIMARY KEY,
    pool_code varchar(40) NOT NULL,
    pool_name varchar(120) NOT NULL,
    org_id uuid,
    balance_amount numeric(18,2) NOT NULL DEFAULT 0,
    total_in_amount numeric(18,2) NOT NULL DEFAULT 0,
    total_offset_amount numeric(18,2) NOT NULL DEFAULT 0,
    allow_pre_offset boolean NOT NULL DEFAULT false,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_rebate_fund_pool_code UNIQUE (pool_code)
);

CREATE TABLE ivs.rebate_fund_supplier_balance (
    id uuid PRIMARY KEY,
    pool_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    balance_amount numeric(18,2) NOT NULL DEFAULT 0,
    total_in_amount numeric(18,2) NOT NULL DEFAULT 0,
    total_offset_amount numeric(18,2) NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_rebate_fund_supplier UNIQUE (pool_id, supplier_id)
);

CREATE TABLE ivs.rebate_fund_txn (
    id uuid PRIMARY KEY,
    pool_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    direction varchar(10) NOT NULL,
    txn_type varchar(30) NOT NULL,
    amount numeric(18,2) NOT NULL,
    balance_after numeric(18,2) NOT NULL,
    remaining_amount numeric(18,2) NOT NULL DEFAULT 0,
    source_type varchar(40),
    source_id uuid,
    source_no varchar(60),
    offset_no varchar(40),
    purchase_order_id uuid,
    purchase_receipt_id uuid,
    occurred_at timestamp with time zone NOT NULL,
    operator_id uuid,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_rebate_txn_supplier ON ivs.rebate_fund_txn (supplier_id, direction, occurred_at);
CREATE INDEX idx_rebate_txn_offset ON ivs.rebate_fund_txn (offset_no);
CREATE INDEX idx_rebate_txn_source ON ivs.rebate_fund_txn (source_type, source_no);

CREATE TABLE ivs.rebate_offset_order (
    id uuid PRIMARY KEY,
    offset_no varchar(40) NOT NULL,
    pool_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    purchase_order_id uuid,
    purchase_receipt_id uuid,
    receipt_no varchar(40),
    offset_amount numeric(18,2) NOT NULL,
    applied_amount numeric(18,2) NOT NULL,
    balance_after numeric(18,2) NOT NULL,
    status varchar(20) NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_rebate_offset_no UNIQUE (offset_no)
);
CREATE INDEX idx_rebate_offset_supplier ON ivs.rebate_offset_order (supplier_id, created_at);
CREATE INDEX idx_rebate_offset_receipt ON ivs.rebate_offset_order (purchase_receipt_id);

CREATE TABLE ivs.rebate_offset_line (
    id uuid PRIMARY KEY,
    offset_id uuid NOT NULL,
    fund_txn_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_rebate_offset_line_offset ON ivs.rebate_offset_line (offset_id);

ALTER TABLE ivs.purchase_receipt ADD COLUMN offset_amount numeric(18,2) NOT NULL DEFAULT 0;