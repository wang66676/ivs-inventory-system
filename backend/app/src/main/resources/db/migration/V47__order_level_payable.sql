UPDATE ivs.payable p
   SET order_no = (
        SELECT COALESCE(po.external_order_no, po.order_no)
          FROM ivs.purchase_order po
         WHERE po.id = p.purchase_order_id
       ),
       amount = (
        SELECT po.total_amount
          FROM ivs.purchase_order po
         WHERE po.id = p.purchase_order_id
       )
 WHERE p.purchase_order_id IS NOT NULL
   AND EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = p.purchase_order_id);

CREATE INDEX IF NOT EXISTS idx_payable_purchase_order ON ivs.payable (purchase_order_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payable_purchase_order ON ivs.payable (purchase_order_id);