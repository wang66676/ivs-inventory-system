ALTER TABLE ivs.payable ADD COLUMN rebate_credit_amount numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE ivs.purchase_invoice ADD COLUMN payable_applied_amount numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE ivs.purchase_invoice ADD COLUMN fund_pool_amount numeric(18,2) NOT NULL DEFAULT 0;

CREATE TABLE ivs.purchase_invoice_rebate_line (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL,
    claim_id uuid,
    claim_line_id uuid,
    entitlement_id uuid,
    serial_no varchar(60) NOT NULL,
    purchase_order_id uuid,
    purchase_receipt_id uuid,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_purchase_invoice_rebate_line_invoice
    ON ivs.purchase_invoice_rebate_line (invoice_id, serial_no);
