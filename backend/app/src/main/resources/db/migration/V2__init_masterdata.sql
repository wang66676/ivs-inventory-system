-- V2：主数据 —— 产品大项、商品、属性模板、序列号档案、仓库、供应商、客户、价目表
-- 说明：H2（PostgreSQL 兼容模式）与 PostgreSQL 双兼容，JSON 字段用 text 承载，
--       生产环境可 ALTER ... TYPE jsonb（应用层读写 JSON 字符串不受影响）。

CREATE SEQUENCE IF NOT EXISTS ivs.product_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.product_category (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    tracking_mode varchar(20) NOT NULL,
    unit varchar(20) NOT NULL,
    default_tax_rate numeric(8,4),
    status_template varchar(40) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    remark varchar(300),
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_category_code UNIQUE (code)
);

CREATE TABLE ivs.product_attr_def (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL,
    code varchar(60) NOT NULL,
    name varchar(120) NOT NULL,
    data_type varchar(20) NOT NULL,
    required boolean NOT NULL DEFAULT false,
    options_json text,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_attr_def_category_code UNIQUE (category_id, code)
);

CREATE TABLE ivs.product (
    id uuid PRIMARY KEY,
    product_no varchar(40) NOT NULL,
    name varchar(200) NOT NULL,
    category_id uuid NOT NULL,
    brand varchar(80),
    series varchar(80),
    model varchar(120),
    config_code varchar(100),
    unit varchar(20) NOT NULL,
    tax_rate numeric(8,4),
    min_price numeric(18,2),
    sale_price_guide numeric(18,2),
    purchase_price_ref numeric(18,2),
    spec_json text,
    remark varchar(300),
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_no UNIQUE (product_no)
);
CREATE INDEX idx_product_category ON ivs.product (category_id, status);
CREATE INDEX idx_product_brand ON ivs.product (brand, series, status);

CREATE TABLE ivs.warehouse (
    id uuid PRIMARY KEY,
    code varchar(60) NOT NULL,
    name varchar(150) NOT NULL,
    type varchar(20) NOT NULL,
    address varchar(300),
    owner_org_id uuid,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_warehouse_code UNIQUE (code)
);

CREATE TABLE ivs.product_serial (
    id uuid PRIMARY KEY,
    serial_no varchar(60) NOT NULL,
    product_id uuid NOT NULL,
    status varchar(30) NOT NULL,
    warehouse_id uuid,
    location_code varchar(60),
    batch_no varchar(60),
    purchase_price numeric(18,2),
    inbound_at timestamp with time zone,
    warranty_start timestamp with time zone,
    warranty_end timestamp with time zone,
    ownership_type varchar(20) NOT NULL,
    lease_contract_id uuid,
    attrs_json text,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_serial_no UNIQUE (serial_no)
);
CREATE INDEX idx_serial_product ON ivs.product_serial (product_id, status);
CREATE INDEX idx_serial_warehouse ON ivs.product_serial (warehouse_id, status);
CREATE INDEX idx_serial_inbound ON ivs.product_serial (inbound_at);

CREATE SEQUENCE IF NOT EXISTS ivs.supplier_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.supplier (
    id uuid PRIMARY KEY,
    code varchar(60) NOT NULL,
    name varchar(200) NOT NULL,
    supplier_type varchar(20) NOT NULL,
    contact_name varchar(80),
    contact_phone varchar(40),
    tax_no varchar(64),
    payment_terms varchar(120),
    remark varchar(300),
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_supplier_code UNIQUE (code)
);

CREATE SEQUENCE IF NOT EXISTS ivs.customer_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.customer (
    id uuid PRIMARY KEY,
    code varchar(60) NOT NULL,
    name varchar(200) NOT NULL,
    customer_type varchar(20) NOT NULL,
    contact_name varchar(80),
    contact_phone varchar(40),
    tax_no varchar(64),
    credit_limit numeric(18,2) NOT NULL DEFAULT 0,
    remark varchar(300),
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_code UNIQUE (code)
);

CREATE TABLE ivs.price_list (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL,
    name varchar(150) NOT NULL,
    customer_type varchar(20),
    effective_from date,
    effective_to date,
    status varchar(20) NOT NULL,
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_price_list_code UNIQUE (code)
);

CREATE TABLE ivs.price_list_line (
    id uuid PRIMARY KEY,
    price_list_id uuid NOT NULL,
    product_id uuid NOT NULL,
    price numeric(18,2) NOT NULL,
    min_price numeric(18,2),
    remark varchar(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_price_list_line UNIQUE (price_list_id, product_id)
);
CREATE INDEX idx_price_list_line_list ON ivs.price_list_line (price_list_id);