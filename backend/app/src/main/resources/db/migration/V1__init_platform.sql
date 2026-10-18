-- V1：平台基础 —— 组织、身份、权限、会话、Outbox/Inbox
-- 与《进销存系统落地方案 v1.0》第 11.2 节模块边界一致：平台能力独立于业务模块。
CREATE SCHEMA IF NOT EXISTS ivs;

CREATE TABLE ivs.outbox_event (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    event_type varchar(120) NOT NULL,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    tenant_id varchar(64),
    trace_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    payload_json text NOT NULL,
    status varchar(20) NOT NULL,
    attempts integer NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error varchar(1000)
);
CREATE INDEX idx_outbox_pending ON ivs.outbox_event (status, next_attempt_at, created_at);

CREATE TABLE ivs.inbox_event (
    id uuid PRIMARY KEY,
    consumer varchar(120) NOT NULL,
    event_id uuid NOT NULL,
    processed_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_inbox_consumer_event UNIQUE (consumer, event_id)
);

CREATE TABLE ivs.company (
    id uuid PRIMARY KEY,
    code varchar(60) NOT NULL UNIQUE,
    name varchar(150) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE ivs.store (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL,
    code varchar(60) NOT NULL,
    name varchar(150) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_store_company_code UNIQUE (company_id, code)
);
CREATE INDEX idx_store_company ON ivs.store (company_id, status);

CREATE TABLE ivs.team (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL,
    store_id uuid NOT NULL,
    code varchar(60) NOT NULL,
    name varchar(150) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_team_store_code UNIQUE (store_id, code)
);
CREATE INDEX idx_team_store ON ivs.team (store_id, status);

CREATE TABLE ivs.platform_user (
    id uuid PRIMARY KEY,
    username varchar(80) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL,
    password_hash varchar(100) NOT NULL,
    company_id uuid,
    store_id uuid,
    team_id uuid,
    data_scope varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_platform_user_company ON ivs.platform_user (company_id, status);
CREATE INDEX idx_platform_user_store ON ivs.platform_user (store_id, status);
CREATE INDEX idx_platform_user_team ON ivs.platform_user (team_id, status);

CREATE TABLE ivs.platform_user_role (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    role_code varchar(60) NOT NULL,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_code)
);

CREATE TABLE ivs.platform_role_permission (
    id uuid PRIMARY KEY,
    role_code varchar(60) NOT NULL,
    permission_code varchar(80) NOT NULL,
    CONSTRAINT uk_role_permission UNIQUE (role_code, permission_code)
);

CREATE TABLE ivs.user_session (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    user_id uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_user_session_user ON ivs.user_session (user_id, expires_at);