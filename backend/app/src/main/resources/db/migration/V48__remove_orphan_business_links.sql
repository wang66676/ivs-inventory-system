-- 仅清理父级业务单据已经不存在、因而不再成立的孤立下游数据。

DELETE FROM ivs.retail_reversal
 WHERE retail_id IN (
     SELECT rr.id FROM ivs.retail_record rr
      WHERE rr.order_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = rr.order_id)
 );

DELETE FROM ivs.retail_record rr
 WHERE rr.order_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = rr.order_id);

DELETE FROM ivs.receipt_allocation ra
 WHERE ra.receivable_id IN (
     SELECT r.id FROM ivs.receivable r
      WHERE (r.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = r.order_id))
         OR (r.delivery_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.sales_delivery d WHERE d.id = r.delivery_id))
 );

DELETE FROM ivs.receivable r
 WHERE (r.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = r.order_id))
    OR (r.delivery_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.sales_delivery d WHERE d.id = r.delivery_id));

DELETE FROM ivs.sales_invoice si
 WHERE (si.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = si.order_id))
    OR (si.delivery_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.sales_delivery d WHERE d.id = si.delivery_id));

DELETE FROM ivs.sales_return_line srl
 WHERE NOT EXISTS (SELECT 1 FROM ivs.sales_return sr WHERE sr.id = srl.return_id);

DELETE FROM ivs.sales_return sr
 WHERE sr.delivery_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM ivs.sales_delivery d WHERE d.id = sr.delivery_id);

DELETE FROM ivs.sales_delivery_line sdl
 WHERE NOT EXISTS (SELECT 1 FROM ivs.sales_delivery d WHERE d.id = sdl.delivery_id);

DELETE FROM ivs.sales_delivery d
 WHERE NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = d.order_id);

DELETE FROM ivs.installment_schedule s
 WHERE NOT EXISTS (SELECT 1 FROM ivs.installment_plan p WHERE p.id = s.plan_id);

DELETE FROM ivs.installment_plan p
 WHERE NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = p.order_id);

DELETE FROM ivs.approval_task t
 WHERE t.business_type IN ('SALES_ORDER','PURCHASE_ORDER')
   AND NOT EXISTS (
       SELECT 1 FROM ivs.sales_order so WHERE t.business_type = 'SALES_ORDER' AND so.id = t.business_id
       UNION ALL
       SELECT 1 FROM ivs.purchase_order po WHERE t.business_type = 'PURCHASE_ORDER' AND po.id = t.business_id
   );

DELETE FROM ivs.user_notification n
 WHERE n.business_type IN ('SALES_ORDER','PURCHASE_ORDER')
   AND NOT EXISTS (
       SELECT 1 FROM ivs.sales_order so WHERE n.business_type = 'SALES_ORDER' AND so.id = n.business_id
       UNION ALL
       SELECT 1 FROM ivs.purchase_order po WHERE n.business_type = 'PURCHASE_ORDER' AND po.id = n.business_id
   );

DELETE FROM ivs.stock_lock l
 WHERE l.biz_type = 'SALES_ORDER'
   AND NOT EXISTS (SELECT 1 FROM ivs.sales_order so WHERE so.id = l.biz_id);

DELETE FROM ivs.purchase_receipt_line prl
 WHERE (prl.receipt_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.purchase_receipt pr WHERE pr.id = prl.receipt_id))
    OR (prl.order_line_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ivs.purchase_order_line pol WHERE pol.id = prl.order_line_id));

DELETE FROM ivs.purchase_receipt pr
 WHERE NOT EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = pr.order_id);

DELETE FROM ivs.purchase_order_line_policy link
 WHERE NOT EXISTS (SELECT 1 FROM ivs.purchase_order_line pol WHERE pol.id = link.order_line_id);

DELETE FROM ivs.purchase_order_line pol
 WHERE NOT EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = pol.order_id);

DELETE FROM ivs.payable_invoice_match m
 WHERE NOT EXISTS (SELECT 1 FROM ivs.payable p WHERE p.id = m.payable_id);

DELETE FROM ivs.payable p
 WHERE p.purchase_order_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = p.purchase_order_id);

DELETE FROM ivs.purchase_invoice_rebate_line ril
 WHERE NOT EXISTS (SELECT 1 FROM ivs.purchase_invoice pi WHERE pi.id = ril.invoice_id)
    OR (ril.purchase_order_id IS NOT NULL AND NOT EXISTS
        (SELECT 1 FROM ivs.purchase_order po WHERE po.id = ril.purchase_order_id));

DELETE FROM ivs.purchase_invoice pi
 WHERE pi.purchase_order_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = pi.purchase_order_id);

DELETE FROM ivs.policy_pre_entitlement pre
 WHERE NOT EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = pre.order_id);

DELETE FROM ivs.policy_entitlement ent
 WHERE ent.order_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM ivs.purchase_order po WHERE po.id = ent.order_id);
