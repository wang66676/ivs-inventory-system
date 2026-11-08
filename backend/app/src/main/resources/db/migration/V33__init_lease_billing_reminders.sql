-- V33：纯电池租赁月租自动生成与提醒去重。
CREATE TABLE ivs.lease_reminder_log (
    id uuid PRIMARY KEY,
    schedule_id uuid NOT NULL,
    reminder_type varchar(20) NOT NULL,
    reminder_date date NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_lease_reminder_day UNIQUE (schedule_id, reminder_type, reminder_date)
);
CREATE INDEX idx_lease_reminder_schedule ON ivs.lease_reminder_log (schedule_id, reminder_date);
