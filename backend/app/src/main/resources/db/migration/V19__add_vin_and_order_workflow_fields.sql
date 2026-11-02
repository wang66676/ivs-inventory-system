-- V19：采购配置/分仓、VIN 合格证日期、销售订单锁货状态
ALTER TABLE ivs.purchase_order_line ADD COLUMN config_text varchar(200);
ALTER TABLE ivs.purchase_order_line ADD COLUMN warehouse_id uuid;

ALTER TABLE ivs.purchase_receipt_line ADD COLUMN warehouse_id uuid;
ALTER TABLE ivs.purchase_receipt_line ADD COLUMN certificate_application_date date;

ALTER TABLE ivs.product_serial ADD COLUMN certificate_application_date date;

ALTER TABLE ivs.sales_order ADD COLUMN locked_at timestamp with time zone;
