-- V20：采购明细按一车一 VIN 录入，并预留申请合格证日期
ALTER TABLE ivs.purchase_order_line ADD COLUMN serial_no varchar(60);
ALTER TABLE ivs.purchase_order_line ADD COLUMN certificate_application_date date;

CREATE UNIQUE INDEX uk_purchase_order_line_serial_no
    ON ivs.purchase_order_line (serial_no);
