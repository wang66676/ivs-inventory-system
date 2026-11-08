-- V34：纯电池租赁押金台账与归还结算。
CREATE TABLE ivs.lease_deposit_txn (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL,
    txn_type varchar(20) NOT NULL,
    amount numeric(18,2) NOT NULL,
    txn_date date NOT NULL,
    method varchar(30),
    reference_no varchar(60),
    remark varchar(300),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_lease_deposit_contract ON ivs.lease_deposit_txn (contract_id, txn_date, created_at);

CREATE TABLE ivs.lease_settlement (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL,
    settlement_date date NOT NULL,
    outstanding_before numeric(18,2) NOT NULL,
    deposit_applied numeric(18,2) NOT NULL,
    outstanding_after numeric(18,2) NOT NULL,
    deposit_refundable numeric(18,2) NOT NULL,
    reason varchar(300),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_lease_settlement_contract UNIQUE (contract_id)
);
