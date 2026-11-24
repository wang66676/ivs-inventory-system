-- V49：软件试用与离线注册授权
CREATE TABLE ivs.software_license (
    id varchar(20) PRIMARY KEY,
    installation_id uuid NOT NULL,
    trial_started_at timestamp with time zone NOT NULL,
    trial_ends_at timestamp with time zone NOT NULL,
    licensed_until timestamp with time zone,
    activation_code text,
    customer_name varchar(200),
    activated_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_software_license_installation UNIQUE (installation_id)
);
