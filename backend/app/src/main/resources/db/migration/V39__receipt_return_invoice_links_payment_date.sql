ALTER TABLE ivs.payable ADD COLUMN last_payment_date date;
ALTER TABLE ivs.purchase_invoice ADD COLUMN purchase_order_id uuid;
ALTER TABLE ivs.purchase_invoice ADD COLUMN serial_nos text;
CREATE UNIQUE INDEX ux_purchase_order_external_order_no ON ivs.purchase_order (external_order_no);
