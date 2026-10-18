-- V12：库存盘点（M5）—— 账面快照、实盘录入、差异过账
CREATE SEQUENCE IF NOT EXISTS ivs.stocktake_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.stocktake (
    id uuid PRIMARY KEY,
    stocktake_no varchar(40) NOT NULL,
    warehouse_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    line_count integer NOT NULL DEFAULT 0,
    difference_count integer NOT NULL DEFAULT 0,
    total_difference_qty numeric(18,3) NOT NULL DEFAULT 0,
    remark varchar(300),
    posted_by uuid,
    posted_at timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_stocktake_no UNIQUE (stocktake_no)
);
CREATE INDEX idx_stocktake_status ON ivs.stocktake (status, created_at);
CREATE INDEX idx_stocktake_warehouse ON ivs.stocktake (warehouse_id, created_at);

CREATE TABLE ivs.stocktake_line (
    id uuid PRIMARY KEY,
    stocktake_id uuid NOT NULL,
    product_id uuid NOT NULL,
    batch_no varchar(60) NOT NULL,
    book_qty numeric(18,3) NOT NULL,
    actual_qty numeric(18,3) NOT NULL,
    difference_qty numeric(18,3) NOT NULL,
    unit_cost numeric(18,2) NOT NULL,
    difference_reason varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_stocktake_line UNIQUE (stocktake_id, product_id, batch_no)
);
CREATE INDEX idx_stocktake_line_stocktake ON ivs.stocktake_line (stocktake_id);
