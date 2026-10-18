-- V16：供应商应付台账与应付期初
CREATE SEQUENCE IF NOT EXISTS ivs.payable_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.payable (
    id uuid PRIMARY KEY,
    payable_no varchar(40) NOT NULL,
    supplier_id uuid NOT NULL,
    purchase_order_id uuid,
    purchase_receipt_id uuid,
    order_no varchar(40),
    receipt_no varchar(40),
    amount numeric(18,2) NOT NULL,
    paid_amount numeric(18,2) NOT NULL DEFAULT 0,
    due_date date,
    status varchar(20) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_payable_no UNIQUE (payable_no),
    CONSTRAINT uk_payable_receipt UNIQUE (purchase_receipt_id)
);
CREATE INDEX idx_payable_supplier ON ivs.payable (supplier_id, status, due_date);
