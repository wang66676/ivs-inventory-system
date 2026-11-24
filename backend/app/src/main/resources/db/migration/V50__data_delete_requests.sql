-- V50：非管理员删除业务数据前必须由管理员审批
CREATE TABLE ivs.data_delete_request (
    id uuid PRIMARY KEY,
    business_type varchar(40) NOT NULL,
    business_id uuid NOT NULL,
    business_no varchar(100) NOT NULL,
    module_name varchar(80) NOT NULL,
    reason varchar(500),
    requester_id uuid NOT NULL,
    requester_name varchar(120) NOT NULL,
    status varchar(20) NOT NULL,
    reviewer_id uuid,
    reviewer_name varchar(120),
    review_remark varchar(500),
    requested_at timestamp with time zone NOT NULL,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_delete_request_status_time ON ivs.data_delete_request (status, requested_at);
CREATE INDEX idx_delete_request_requester ON ivs.data_delete_request (requester_id, requested_at);
CREATE INDEX idx_delete_request_business
    ON ivs.data_delete_request (business_type, business_id, status);
