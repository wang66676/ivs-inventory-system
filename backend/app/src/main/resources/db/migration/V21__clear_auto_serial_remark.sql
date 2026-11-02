-- 清理旧版本采购收货自动写入的技术性备注；备注字段只保留业务人工填写内容。
UPDATE ivs.product_serial
SET remark = NULL
WHERE remark = '采购收货自动建档';
