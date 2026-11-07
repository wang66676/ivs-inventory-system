-- V32：二期电池租赁资产池、租赁合同与月租计划。
CREATE SEQUENCE IF NOT EXISTS ivs.lease_contract_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.lease_contract (
    id uuid PRIMARY KEY,
    contract_no varchar(40) NOT NULL,
    customer_id uuid NOT NULL,
    serial_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    monthly_fee numeric(18,2) NOT NULL,
    deposit numeric(18,2) NOT NULL DEFAULT 0,
    ownership_at_end varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_lease_contract_no UNIQUE (contract_no)
);
CREATE INDEX idx_lease_contract_customer ON ivs.lease_contract (customer_id, status);
CREATE INDEX idx_lease_contract_serial ON ivs.lease_contract (serial_id, status);

CREATE TABLE ivs.lease_schedule (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL,
    period_no integer NOT NULL,
    due_date date NOT NULL,
    amount numeric(18,2) NOT NULL,
    receivable_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_lease_schedule_period UNIQUE (contract_id, period_no)
);
CREATE INDEX idx_lease_schedule_due ON ivs.lease_schedule (due_date, receivable_id);
