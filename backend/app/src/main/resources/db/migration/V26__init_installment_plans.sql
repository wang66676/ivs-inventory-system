-- 一期分期收款 / 以租代售：首付、分期应收计划与期次。

ALTER TABLE ivs.sales_order ADD COLUMN payment_mode varchar(20) NOT NULL DEFAULT 'CASH';
ALTER TABLE ivs.sales_order ADD COLUMN down_payment numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE ivs.sales_order ADD COLUMN installment_count integer NOT NULL DEFAULT 0;
ALTER TABLE ivs.sales_order ADD COLUMN first_due_date date;

CREATE SEQUENCE IF NOT EXISTS ivs.installment_plan_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.installment_plan (
    id uuid PRIMARY KEY,
    plan_no varchar(40) NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    total_amount numeric(18,2) NOT NULL,
    down_payment numeric(18,2) NOT NULL,
    financed_amount numeric(18,2) NOT NULL,
    installment_count integer NOT NULL,
    first_due_date date NOT NULL,
    status varchar(20) NOT NULL,
    settled_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_installment_plan_no UNIQUE (plan_no),
    CONSTRAINT uk_installment_plan_order UNIQUE (order_id)
);

CREATE TABLE ivs.installment_schedule (
    id uuid PRIMARY KEY,
    plan_id uuid NOT NULL,
    period_no integer NOT NULL,
    receivable_id uuid NOT NULL,
    due_date date NOT NULL,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_installment_schedule_period UNIQUE (plan_id, period_no),
    CONSTRAINT uk_installment_schedule_receivable UNIQUE (receivable_id)
);

CREATE INDEX idx_installment_schedule_plan ON ivs.installment_schedule (plan_id, period_no);
CREATE INDEX idx_installment_plan_status ON ivs.installment_plan (status, first_due_date);
