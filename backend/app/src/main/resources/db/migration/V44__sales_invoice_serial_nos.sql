ALTER TABLE ivs.sales_invoice ADD COLUMN serial_nos text;
CREATE INDEX idx_sales_invoice_order_serial ON ivs.sales_invoice (order_id, created_at);
