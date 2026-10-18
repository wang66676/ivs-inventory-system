-- V4：采购与库存 —— 采购单/收货单/库存台账/库存余额/库存锁定
-- 说明：stock_ledger 为只追加事实表（无 updated_at/version），stock_balance 为可重建投影。

CREATE SEQUENCE IF NOT EXISTS ivs.purchase_order_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.purchase_receipt_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.purchase_order (
    id uuid PRIMARY KEY,
    order_no varchar(40) NOT NULL,
    supplier_id uuid NOT NULL,
    warehouse_id uuid,
    status varchar(30) NOT NULL,
    total_amount numeric(18,2) NOT NULL,
    expected_date date,
    remark varchar(300),
    owner_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_order_no UNIQUE (order_no)
);
CREATE INDEX idx_purchase_order_status ON ivs.purchase_order (status, created_at);
CREATE INDEX idx_purchase_order_supplier ON ivs.purchase_order (supplier_id, status);

CREATE TABLE ivs.purchase_order_line (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    line_no integer NOT NULL,
    product_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    received_quantity numeric(18,3) NOT NULL DEFAULT 0,
    unit_price numeric(18,2) NOT NULL,
    tax_rate numeric(8,4),
    amount numeric(18,2) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_purchase_order_line_order ON ivs.purchase_order_line (order_id, line_no);

CREATE TABLE ivs.purchase_receipt (
    id uuid PRIMARY KEY,
    receipt_no varchar(40) NOT NULL,
    order_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    warehouse_id uuid,
    status varchar(20) NOT NULL,
    received_at timestamp with time zone NOT NULL,
    amount numeric(18,2) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_receipt_no UNIQUE (receipt_no)
);
CREATE INDEX idx_purchase_receipt_order ON ivs.purchase_receipt (order_id, received_at);

CREATE TABLE ivs.purchase_receipt_line (
    id uuid PRIMARY KEY,
    receipt_id uuid NOT NULL,
    order_line_id uuid,
    product_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,2) NOT NULL,
    batch_no varchar(60),
    serial_nos text,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_purchase_receipt_line_receipt ON ivs.purchase_receipt_line (receipt_id);

CREATE TABLE ivs.stock_ledger (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    serial_id uuid,
    warehouse_id uuid NOT NULL,
    move_type varchar(30) NOT NULL,
    direction varchar(10) NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_cost numeric(18,2) NOT NULL,
    amount numeric(18,2) NOT NULL,
    balance_after numeric(18,3) NOT NULL,
    biz_type varchar(40),
    biz_id uuid,
    biz_no varchar(60),
    occurred_at timestamp with time zone NOT NULL,
    operator_id uuid,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_ledger_product_time ON ivs.stock_ledger (product_id, occurred_at);
CREATE INDEX idx_ledger_serial ON ivs.stock_ledger (serial_id);
CREATE INDEX idx_ledger_biz ON ivs.stock_ledger (biz_type, biz_id);
CREATE INDEX idx_ledger_warehouse ON ivs.stock_ledger (warehouse_id, occurred_at);

CREATE TABLE ivs.stock_balance (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    batch_no varchar(60) NOT NULL,
    on_hand_qty numeric(18,3) NOT NULL,
    locked_qty numeric(18,3) NOT NULL,
    available_qty numeric(18,3) NOT NULL,
    unit_cost numeric(18,2) NOT NULL,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_balance UNIQUE (product_id, warehouse_id, batch_no)
);
CREATE INDEX idx_stock_balance_warehouse ON ivs.stock_balance (warehouse_id, product_id);

CREATE TABLE ivs.stock_lock (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    serial_id uuid,
    warehouse_id uuid NOT NULL,
    batch_no varchar(60),
    quantity numeric(18,3) NOT NULL,
    status varchar(20) NOT NULL,
    biz_type varchar(40),
    biz_id uuid,
    biz_no varchar(60),
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_stock_lock_status ON ivs.stock_lock (status, expires_at);
CREATE INDEX idx_stock_lock_biz ON ivs.stock_lock (biz_id, status);