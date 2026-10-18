-- V14：采购退货与销售退货（M5）
CREATE SEQUENCE IF NOT EXISTS ivs.purchase_return_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.sales_return_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.purchase_return (
    id uuid PRIMARY KEY,
    return_no varchar(40) NOT NULL,
    supplier_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    total_amount numeric(18,2) NOT NULL,
    reason varchar(300),
    remark varchar(300),
    posted_by uuid,
    posted_at timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_return_no UNIQUE (return_no)
);
CREATE INDEX idx_purchase_return_supplier ON ivs.purchase_return (supplier_id, created_at);
CREATE INDEX idx_purchase_return_warehouse ON ivs.purchase_return (warehouse_id, created_at);

CREATE TABLE ivs.purchase_return_line (
    id uuid PRIMARY KEY,
    return_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,2) NOT NULL,
    amount numeric(18,2) NOT NULL,
    batch_no varchar(60),
    serial_nos text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_purchase_return_line_return ON ivs.purchase_return_line (return_id);

CREATE TABLE ivs.sales_return (
    id uuid PRIMARY KEY,
    return_no varchar(40) NOT NULL,
    customer_id uuid NOT NULL,
    delivery_id uuid,
    warehouse_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    total_amount numeric(18,2) NOT NULL,
    cost_amount numeric(18,2) NOT NULL,
    receivable_adjustment_amount numeric(18,2) NOT NULL DEFAULT 0,
    refund_amount numeric(18,2) NOT NULL DEFAULT 0,
    reason varchar(300),
    remark varchar(300),
    posted_by uuid,
    posted_at timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_return_no UNIQUE (return_no)
);
CREATE INDEX idx_sales_return_customer ON ivs.sales_return (customer_id, created_at);
CREATE INDEX idx_sales_return_delivery ON ivs.sales_return (delivery_id);

CREATE TABLE ivs.sales_return_line (
    id uuid PRIMARY KEY,
    return_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,2) NOT NULL,
    amount numeric(18,2) NOT NULL,
    unit_cost numeric(18,2) NOT NULL,
    cost_amount numeric(18,2) NOT NULL,
    batch_no varchar(60),
    serial_nos text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_sales_return_line_return ON ivs.sales_return_line (return_id);
