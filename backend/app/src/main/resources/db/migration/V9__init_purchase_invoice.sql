-- V9：进项（厂家）红字发票登记，登记后自动进入返利资金池
CREATE SEQUENCE IF NOT EXISTS ivs.purchase_invoice_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.purchase_invoice (
    id uuid PRIMARY KEY,
    invoice_no varchar(40) NOT NULL,
    direction varchar(10) NOT NULL,
    type varchar(10) NOT NULL,
    original_invoice_id uuid,
    original_invoice_no varchar(100),
    supplier_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    tax_rate numeric(8,4),
    tax_amount numeric(18,2),
    status varchar(20) NOT NULL,
    claim_id uuid,
    external_invoice_no varchar(100),
    issued_at timestamp with time zone,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_invoice_no UNIQUE (invoice_no)
);
CREATE INDEX idx_purchase_invoice_supplier ON ivs.purchase_invoice (supplier_id, created_at);