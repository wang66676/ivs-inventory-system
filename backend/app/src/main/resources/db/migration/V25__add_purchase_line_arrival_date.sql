-- 电池采购明细按行维护预计到货时间；整车采购明细保持原结构。
ALTER TABLE ivs.purchase_order_line ADD COLUMN arrival_date date;
