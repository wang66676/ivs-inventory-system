-- V8：政策与预享受项 —— 政策录入、额度池（时间/金额/条件）、订单选政策
CREATE SEQUENCE IF NOT EXISTS ivs.policy_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ivs.pre_entitlement_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.promotion_policy (
    id uuid PRIMARY KEY,
    policy_no varchar(40) NOT NULL,
    name varchar(150) NOT NULL,
    policy_type varchar(30) NOT NULL,
    category_id uuid,
    model_list text,
    benefit_type varchar(20) NOT NULL,
    benefit_value numeric(18,4) NOT NULL,
    max_amount numeric(18,2),
    eff_from date NOT NULL,
    eff_to date NOT NULL,
    reference_date_type varchar(20) NOT NULL,
    claim_cycle varchar(20) NOT NULL,
    claim_cutoff_day integer NOT NULL DEFAULT 5,
    priority integer NOT NULL DEFAULT 0,
    stacking_rule varchar(20) NOT NULL,
    cost_bearer varchar(20) NOT NULL,
    cost_subject varchar(40),
    status varchar(30) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_policy_no UNIQUE (policy_no)
);
CREATE INDEX idx_policy_status ON ivs.promotion_policy (status, eff_from, eff_to);

CREATE TABLE ivs.policy_subsidy_pool (
    id uuid PRIMARY KEY,
    policy_id uuid NOT NULL,
    limit_type varchar(30) NOT NULL,
    total_amount numeric(18,2),
    used_amount numeric(18,2) NOT NULL DEFAULT 0,
    remaining_amount numeric(18,2),
    per_unit_amount numeric(18,2),
    condition_desc varchar(300),
    condition_met boolean NOT NULL DEFAULT false,
    effective_from date,
    effective_to date,
    exhaust_rule varchar(20) NOT NULL DEFAULT 'PARTIAL',
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_policy_pool UNIQUE (policy_id)
);

CREATE TABLE ivs.policy_subsidy_pool_usage (
    id uuid PRIMARY KEY,
    pool_id uuid NOT NULL,
    source_type varchar(30) NOT NULL,
    source_id uuid,
    source_no varchar(60),
    amount numeric(18,2) NOT NULL,
    used_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_policy_pool_usage_pool ON ivs.policy_subsidy_pool_usage (pool_id, used_at);

CREATE TABLE ivs.policy_pre_entitlement (
    id uuid PRIMARY KEY,
    pre_no varchar(40) NOT NULL,
    order_id uuid NOT NULL,
    order_line_id uuid,
    serial_id uuid,
    product_id uuid NOT NULL,
    policy_id uuid NOT NULL,
    estimate_amount numeric(18,2) NOT NULL,
    status varchar(30) NOT NULL,
    added_after_order boolean NOT NULL DEFAULT false,
    added_by uuid,
    added_at timestamp with time zone,
    add_reason varchar(300),
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_pre_no UNIQUE (pre_no),
    CONSTRAINT uk_pre_order_line_policy UNIQUE (order_id, order_line_id, policy_id)
);
CREATE INDEX idx_pre_order ON ivs.policy_pre_entitlement (order_id, status);
CREATE INDEX idx_pre_product ON ivs.policy_pre_entitlement (product_id, status);