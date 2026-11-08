-- V35：纯电池租赁健康档案。
CREATE TABLE ivs.lease_battery_health (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL,
    serial_id uuid NOT NULL,
    record_date date NOT NULL,
    soh_percent numeric(5,2) NOT NULL,
    cycle_count integer NOT NULL,
    mileage_km numeric(12,2) NOT NULL,
    inspection_result varchar(20) NOT NULL,
    inspector varchar(80),
    remark varchar(300),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_lease_health_contract ON ivs.lease_battery_health (contract_id, record_date, created_at);
CREATE INDEX idx_lease_health_serial ON ivs.lease_battery_health (serial_id, record_date);
