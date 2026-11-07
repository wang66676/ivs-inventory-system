-- V28：审批待办与站内通知。通知可按权限人群或指定用户投递，阅读状态按用户隔离。
CREATE SEQUENCE IF NOT EXISTS ivs.approval_task_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ivs.approval_task (
    id uuid PRIMARY KEY,
    task_no varchar(40) NOT NULL,
    task_type varchar(60) NOT NULL,
    business_type varchar(60) NOT NULL,
    business_id uuid NOT NULL,
    business_no varchar(100) NOT NULL,
    title varchar(200) NOT NULL,
    summary varchar(1000),
    amount numeric(18,2),
    approval_permission varchar(80) NOT NULL,
    status varchar(20) NOT NULL,
    applicant_id uuid,
    applicant_name varchar(120) NOT NULL,
    decision_by_id uuid,
    decision_by_name varchar(120),
    decision_reason varchar(500),
    decided_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_approval_task_no UNIQUE (task_no)
);
CREATE INDEX idx_approval_task_pending ON ivs.approval_task (status, approval_permission, created_at);
CREATE INDEX idx_approval_task_business ON ivs.approval_task (business_type, business_id, status);

CREATE TABLE ivs.user_notification (
    id uuid PRIMARY KEY,
    category varchar(60) NOT NULL,
    title varchar(200) NOT NULL,
    content varchar(1000) NOT NULL,
    business_type varchar(60),
    business_id uuid,
    audience_user_id uuid,
    audience_permission varchar(80),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_user_notification_created ON ivs.user_notification (created_at);
CREATE INDEX idx_user_notification_permission ON ivs.user_notification (audience_permission, created_at);

CREATE TABLE ivs.user_notification_read (
    id uuid PRIMARY KEY,
    notification_id uuid NOT NULL,
    user_id uuid NOT NULL,
    read_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_notification_read_user UNIQUE (notification_id, user_id)
);
CREATE INDEX idx_notification_read_user ON ivs.user_notification_read (user_id, read_at);
