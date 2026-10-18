-- V3：补齐价目表编码序列（V2 遗漏，Flyway 不允许修改已执行的 V2）
CREATE SEQUENCE IF NOT EXISTS ivs.price_list_no_seq START WITH 1 INCREMENT BY 1;