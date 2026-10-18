-- V10：正式权益（实销确认后由预享受项生成，按实销日期归期）
CREATE SEQUENCE IF NOT EXISTS ivs.entitlement_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.policy_entitlement (
    id uuid PRIMARY KEY,
    entitlement_no varchar(40) NOT NULL,
    pre_entitlement_id uuid,
    policy_id uuid NOT NULL,
    retail_id uuid,
    serial_id uuid,
    product_id uuid NOT NULL,
    order_id uuid,
    face_amount numeric(18,2) NOT NULL,
    claimed_amount numeric(18,2) NOT NULL DEFAULT 0,
    confirmed_amount numeric(18,2) NOT NULL DEFAULT 0,
    settled_amount numeric(18,2) NOT NULL DEFAULT 0,
    period_code varchar(20) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_entitlement_no UNIQUE (entitlement_no)
);
CREATE INDEX idx_entitlement_period ON ivs.policy_entitlement (period_code, status);
CREATE INDEX idx_entitlement_retail ON ivs.policy_entitlement (retail_id);