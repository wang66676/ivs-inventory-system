-- 动力电池按“电池箱 + 电池包”组合计价；整车明细保持原有字段不变。

ALTER TABLE ivs.purchase_order_line ADD COLUMN battery_box_manufacturer varchar(200);
ALTER TABLE ivs.purchase_order_line ADD COLUMN battery_box_model varchar(160);
ALTER TABLE ivs.purchase_order_line ADD COLUMN battery_box_unit_price numeric(18,2);
ALTER TABLE ivs.purchase_order_line ADD COLUMN battery_pack_manufacturer varchar(200);
ALTER TABLE ivs.purchase_order_line ADD COLUMN battery_energy_kwh numeric(18,3);
ALTER TABLE ivs.purchase_order_line ADD COLUMN battery_pack_unit_price numeric(18,2);

ALTER TABLE ivs.sales_order_line ADD COLUMN battery_box_manufacturer varchar(200);
ALTER TABLE ivs.sales_order_line ADD COLUMN battery_box_model varchar(160);
ALTER TABLE ivs.sales_order_line ADD COLUMN battery_box_unit_price numeric(18,2);
ALTER TABLE ivs.sales_order_line ADD COLUMN battery_pack_manufacturer varchar(200);
ALTER TABLE ivs.sales_order_line ADD COLUMN battery_energy_kwh numeric(18,3);
ALTER TABLE ivs.sales_order_line ADD COLUMN battery_pack_unit_price numeric(18,2);
