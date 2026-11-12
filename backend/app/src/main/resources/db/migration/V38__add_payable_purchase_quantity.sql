ALTER TABLE ivs.payable ADD COLUMN purchase_quantity numeric(18,3) NOT NULL DEFAULT 0;

UPDATE ivs.payable p
SET purchase_quantity = COALESCE((
    SELECT SUM(prl.quantity)
    FROM ivs.purchase_receipt_line prl
    WHERE prl.receipt_id = p.purchase_receipt_id
), 0);
