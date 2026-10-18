-- V17：管理员全局参数
CREATE TABLE ivs.system_setting (
    setting_key varchar(120) PRIMARY KEY,
    setting_value varchar(500) NOT NULL,
    label varchar(120) NOT NULL,
    setting_group varchar(80) NOT NULL,
    value_type varchar(20) NOT NULL,
    description varchar(300),
    sort_order integer NOT NULL DEFAULT 0,
    updated_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

INSERT INTO ivs.system_setting (setting_key, setting_value, label, setting_group, value_type, description, sort_order, created_at, updated_at, version) VALUES
('system.name', '进销存系统（IVS）', '系统名称', '基础信息', 'STRING', '显示在登录页和工作台', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('default.tax.rate', '13', '默认税率', '业务默认值', 'PERCENT', '新建商品时的默认税率，按百分数填写，如 13 表示 13%', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('sales.payment.term.days', '30', '销售账期（天）', '业务默认值', 'NUMBER', '销售出库生成应收时的默认账期', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('stock.warning.percent', '10', '库存/额度预警比例', '预警阈值', 'PERCENT', '库存或政策额度剩余低于该比例时预警', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('claim.difference.alert.days', '30', '申报差异提醒天数', '预警阈值', 'NUMBER', '申报差异超过该天数仍未处置时提醒', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('security.session.hours', '12', '登录会话时长（小时）', '安全参数', 'NUMBER', '新登录会话的有效期，修改后新会话生效', 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('outbox.dispatch.interval.ms', '3000', '事件投递间隔（毫秒）', '集成参数', 'NUMBER', 'Outbox 事件扫描与投递间隔', 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

