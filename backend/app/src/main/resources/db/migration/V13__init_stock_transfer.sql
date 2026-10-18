-- V13：库存调拨（M5）—— 调出仓审核、在途、调入仓签收与差异
CREATE SEQUENCE IF NOT EXISTS ivs.stock_transfer_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.stock_transfer (
    id uuid PRIMARY KEY,
    transfer_no varchar(40) NOT NULL,
    from_warehouse_id uuid NOT NULL,
    to_warehouse_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    line_count integer NOT NULL DEFAULT 0,
    total_quantity numeric(18,3) NOT NULL DEFAULT 0,
    received_quantity numeric(18,3) NOT NULL DEFAULT 0,
    difference_quantity numeric(18,3) NOT NULL DEFAULT 0,
    remark varchar(300),
    approved_by uuid,
    approved_at timestamp with time zone,
    dispatched_by uuid,
    dispatched_at timestamp with time zone,
    received_by uuid,
    received_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_transfer_no UNIQUE (transfer_no)
);
CREATE INDEX idx_stock_transfer_status ON ivs.stock_transfer (status, created_at);
CREATE INDEX idx_stock_transfer_warehouse ON ivs.stock_transfer (from_warehouse_id, to_warehouse_id);

CREATE TABLE ivs.stock_transfer_line (
    id uuid PRIMARY KEY,
    transfer_id uuid NOT NULL,
    product_id uuid NOT NULL,
    batch_no varchar(60) NOT NULL,
    quantity numeric(18,3) NOT NULL,
    received_quantity numeric(18,3) NOT NULL DEFAULT 0,
    difference_quantity numeric(18,3) NOT NULL DEFAULT 0,
    unit_cost numeric(18,2) NOT NULL DEFAULT 0,
    difference_reason varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_transfer_line UNIQUE (transfer_id, product_id, batch_no)
);
CREATE INDEX idx_stock_transfer_line_transfer ON ivs.stock_transfer_line (transfer_id);
