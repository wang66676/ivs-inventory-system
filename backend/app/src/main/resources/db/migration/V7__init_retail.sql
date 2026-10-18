-- V7：实销登记与复核 —— 政策返利的唯一时间与数量基准
-- 规则：车辆使用人、手机号、终端发票开票时间必填；车牌照号选填；不强制上传行驶证；
--       实销日期必须晚于（或等于）终端发票开票时间；同一序列号同时只能有一条有效实销记录。

CREATE SEQUENCE IF NOT EXISTS ivs.retail_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.retail_record (
    id uuid PRIMARY KEY,
    retail_no varchar(40) NOT NULL,
    retail_date date NOT NULL,
    invoice_issued_at timestamp with time zone NOT NULL,
    register_date date,
    delivery_date date,
    serial_id uuid NOT NULL,
    serial_no varchar(60) NOT NULL,
    product_id uuid NOT NULL,
    customer_id uuid,
    order_id uuid,
    user_name varchar(80) NOT NULL,
    user_mobile varchar(40) NOT NULL,
    plate_no varchar(40),
    sale_price numeric(18,2),
    status varchar(20) NOT NULL,
    active_serial_key uuid,
    verified_by uuid,
    verified_at timestamp with time zone,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_retail_no UNIQUE (retail_no),
    CONSTRAINT uk_retail_active_serial UNIQUE (active_serial_key)
);
CREATE INDEX idx_retail_date ON ivs.retail_record (retail_date, status);
CREATE INDEX idx_retail_serial ON ivs.retail_record (serial_id);
CREATE INDEX idx_retail_customer ON ivs.retail_record (customer_id);
CREATE INDEX idx_retail_order ON ivs.retail_record (order_id);

CREATE TABLE ivs.retail_reversal (
    id uuid PRIMARY KEY,
    retail_id uuid NOT NULL,
    reason varchar(300) NOT NULL,
    new_serial_id uuid,
    reversed_by uuid,
    reversed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_retail_reversal_retail ON ivs.retail_reversal (retail_id);