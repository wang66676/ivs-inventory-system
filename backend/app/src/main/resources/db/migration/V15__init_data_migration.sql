-- V15：历史数据迁移批次与回滚追踪
CREATE SEQUENCE IF NOT EXISTS ivs.data_migration_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.data_migration_batch (
    id uuid PRIMARY KEY,
    batch_no varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    source_system varchar(120),
    status varchar(20) NOT NULL,
    item_count integer NOT NULL DEFAULT 0,
    total_amount numeric(18,2) NOT NULL DEFAULT 0,
    remark varchar(300),
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    imported_at timestamp with time zone,
    rolled_back_at timestamp with time zone,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_data_migration_no UNIQUE (batch_no)
);
CREATE INDEX idx_data_migration_status ON ivs.data_migration_batch (status, created_at);

CREATE TABLE ivs.data_migration_item (
    id uuid PRIMARY KEY,
    batch_id uuid NOT NULL,
    item_type varchar(40) NOT NULL,
    source_key varchar(200) NOT NULL,
    target_id uuid,
    amount numeric(18,2) NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_data_migration_item_batch ON ivs.data_migration_item (batch_id, item_type);
