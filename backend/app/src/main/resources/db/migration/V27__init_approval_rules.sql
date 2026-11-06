-- V27：审批配置中心。规则存放于平台模块，业务模块通过各自端口接入。
CREATE TABLE ivs.approval_rule (
    rule_code varchar(80) PRIMARY KEY,
    rule_name varchar(120) NOT NULL,
    scenario varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    metric_type varchar(30) NOT NULL,
    threshold_value numeric(18,4) NOT NULL DEFAULT 0,
    approval_permission varchar(80) NOT NULL,
    description varchar(500),
    sort_order integer NOT NULL DEFAULT 0,
    updated_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

INSERT INTO ivs.approval_rule
    (rule_code, rule_name, scenario, enabled, metric_type, threshold_value, approval_permission, description, sort_order, created_at, updated_at, version)
VALUES
    ('SALES_LOW_PRICE', '低于最低限价成交', 'SALES', true, 'DISCOUNT_PERCENT', 0, 'IVS_PRICE_APPROVE',
     '成交价低于最低限价且折让比例达到阈值时，必须完成价格审批。阈值 0 表示任何低价均需审批。', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('STOCK_ADJUSTMENT', '库存调整/盘点差异', 'INVENTORY', true, 'AMOUNT', 5000, 'IVS_STOCK_ADJUST',
     '库存调整或盘点差异金额达到阈值时，需要库存审批。', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('POLICY_PUBLISH', '政策审批与发布', 'POLICY', true, 'ALWAYS', 0, 'IVS_POLICY_APPROVE',
     '政策从草稿发布为正式政策时，必须由政策审批权限用户执行。', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('SUBSIDY_POOL_ADJUSTMENT', '政策额度池调整', 'POLICY', true, 'AMOUNT', 10000, 'IVS_SUBSIDY_POOL_MANAGE',
     '单次政策额度池调整金额达到阈值时，需要额度池管理权限用户审批。', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('FUND_POOL_ADJUSTMENT', '返利资金池调整', 'FUND', true, 'AMOUNT', 10000, 'IVS_FUND_POOL_ADJUST',
     '单次返利资金池人工调整金额达到阈值时，需要资金池调整权限用户审批。', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
