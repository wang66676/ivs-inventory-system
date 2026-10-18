-- V5：销售与结算 —— 销售订单 / 出库单 / 应收 / 收款与核销 / 销项发票

CREATE SEQUENCE IF NOT EXISTS ivs.sales_order_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.sales_delivery_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.receivable_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.receipt_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.sales_invoice_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.sales_order (
    id uuid PRIMARY KEY,
    order_no varchar(40) NOT NULL,
    customer_id uuid NOT NULL,
    warehouse_id uuid,
    status varchar(30) NOT NULL,
    total_amount numeric(18,2) NOT NULL,
    price_approval_required boolean NOT NULL DEFAULT false,
    price_approved_by uuid,
    price_approved_at timestamp with time zone,
    remark varchar(300),
    owner_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_order_no UNIQUE (order_no)
);
CREATE INDEX idx_sales_order_customer ON ivs.sales_order (customer_id, created_at);
CREATE INDEX idx_sales_order_status ON ivs.sales_order (status, created_at);

CREATE TABLE ivs.sales_order_line (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    line_no integer NOT NULL,
    product_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    delivered_quantity numeric(18,3) NOT NULL DEFAULT 0,
    unit_price numeric(18,2) NOT NULL,
    min_price numeric(18,2),
    tax_rate numeric(8,4),
    amount numeric(18,2) NOT NULL,
    serial_ids text,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_sales_order_line_order ON ivs.sales_order_line (order_id, line_no);

CREATE TABLE ivs.sales_delivery (
    id uuid PRIMARY KEY,
    delivery_no varchar(40) NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    delivered_at timestamp with time zone NOT NULL,
    amount numeric(18,2) NOT NULL,
    cost_amount numeric(18,2) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_delivery_no UNIQUE (delivery_no)
);
CREATE INDEX idx_sales_delivery_order ON ivs.sales_delivery (order_id, delivered_at);

CREATE TABLE ivs.sales_delivery_line (
    id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,2) NOT NULL,
    cost_amount numeric(18,2) NOT NULL,
    serial_ids text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_sales_delivery_line_delivery ON ivs.sales_delivery_line (delivery_id);

CREATE TABLE ivs.receivable (
    id uuid PRIMARY KEY,
    receivable_no varchar(40) NOT NULL,
    customer_id uuid NOT NULL,
    order_id uuid,
    delivery_id uuid,
    amount numeric(18,2) NOT NULL,
    received_amount numeric(18,2) NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL,
    due_date date,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_receivable_no UNIQUE (receivable_no)
);
CREATE INDEX idx_receivable_customer ON ivs.receivable (customer_id, status, due_date);

CREATE TABLE ivs.receipt (
    id uuid PRIMARY KEY,
    receipt_no varchar(40) NOT NULL,
    customer_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    method varchar(30) NOT NULL,
    received_at timestamp with time zone NOT NULL,
    status varchar(20) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_receipt_no UNIQUE (receipt_no)
);
CREATE INDEX idx_receipt_customer ON ivs.receipt (customer_id, received_at);

CREATE TABLE ivs.receipt_allocation (
    id uuid PRIMARY KEY,
    receipt_id uuid NOT NULL,
    receivable_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_receipt_allocation_receipt ON ivs.receipt_allocation (receipt_id);
CREATE INDEX idx_receipt_allocation_receivable ON ivs.receipt_allocation (receivable_id);

CREATE TABLE ivs.sales_invoice (
    id uuid PRIMARY KEY,
    invoice_no varchar(40) NOT NULL,
    direction varchar(10) NOT NULL,
    type varchar(10) NOT NULL,
    order_id uuid,
    delivery_id uuid,
    customer_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    tax_rate numeric(8,4),
    tax_amount numeric(18,2),
    status varchar(20) NOT NULL,
    original_invoice_id uuid,
    external_invoice_no varchar(100),
    issued_at timestamp with time zone,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_invoice_no UNIQUE (invoice_no)
);
CREATE INDEX idx_sales_invoice_order ON ivs.sales_invoice (order_id, created_at);
CREATE INDEX idx_sales_invoice_customer ON ivs.sales_invoice (customer_id, status);