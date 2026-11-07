-- V30：进项蓝字发票与供应商应付匹配。
ALTER TABLE ivs.purchase_invoice ADD COLUMN matched_amount numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE ivs.payable ADD COLUMN invoice_matched_amount numeric(18,2) NOT NULL DEFAULT 0;

CREATE TABLE ivs.payable_invoice_match (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL,
    payable_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_payable_invoice_match UNIQUE (invoice_id, payable_id)
);
CREATE INDEX idx_payable_invoice_match_invoice ON ivs.payable_invoice_match (invoice_id, created_at);
CREATE INDEX idx_payable_invoice_match_payable ON ivs.payable_invoice_match (payable_id, created_at);
