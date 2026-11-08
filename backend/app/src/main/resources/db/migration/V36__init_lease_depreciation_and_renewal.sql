-- V36：租赁资产直线折旧计划与续租变更留痕。
ALTER TABLE ivs.lease_contract ADD COLUMN purchase_cost numeric(18,2) NOT NULL DEFAULT 0;
UPDATE ivs.lease_contract c
SET purchase_cost = COALESCE((SELECT ps.purchase_price FROM ivs.product_serial ps WHERE ps.id = c.serial_id), 0);

CREATE TABLE ivs.lease_depreciation_schedule (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL,
    period_no integer NOT NULL,
    depreciation_date date NOT NULL,
    amount numeric(18,2) NOT NULL,
    accumulated_amount numeric(18,2) NOT NULL,
    net_value numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_lease_depreciation_period UNIQUE (contract_id, period_no)
);
CREATE INDEX idx_lease_depreciation_contract ON ivs.lease_depreciation_schedule (contract_id, depreciation_date);

CREATE TABLE ivs.lease_contract_change (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL,
    change_type varchar(20) NOT NULL,
    old_end_date date NOT NULL,
    new_end_date date NOT NULL,
    old_monthly_fee numeric(18,2) NOT NULL,
    new_monthly_fee numeric(18,2) NOT NULL,
    reason varchar(300),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_lease_contract_change ON ivs.lease_contract_change (contract_id, created_at);
