-- V37：租赁电池退役、报废与梯次利用登记。
CREATE SEQUENCE IF NOT EXISTS ivs.lease_disposal_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.lease_asset_disposal (
    id uuid PRIMARY KEY,
    disposal_no varchar(40) NOT NULL,
    serial_id uuid NOT NULL,
    contract_id uuid,
    disposition_type varchar(20) NOT NULL,
    disposal_date date NOT NULL,
    soh_percent numeric(5,2) NOT NULL,
    destination varchar(120),
    reason varchar(300) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_lease_disposal_no UNIQUE (disposal_no)
);
CREATE INDEX idx_lease_disposal_serial ON ivs.lease_asset_disposal (serial_id, disposal_date);
CREATE INDEX idx_lease_disposal_contract ON ivs.lease_asset_disposal (contract_id, disposal_date);
