-- 仓库类型调整：保留历史数据映射
UPDATE ivs.warehouse SET type = 'STORE' WHERE type = 'SHOP';
UPDATE ivs.warehouse SET type = 'TEMPORARY' WHERE type = 'TRANSIT';
UPDATE ivs.warehouse SET type = 'OTHER' WHERE type = 'ASSET_POOL';

-- 供应商类型调整：原 OEM 映射为整车厂
UPDATE ivs.supplier SET supplier_type = 'VEHICLE_OEM' WHERE supplier_type = 'OEM';