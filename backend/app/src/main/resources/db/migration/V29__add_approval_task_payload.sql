-- V29：审批任务保存延迟执行的业务载荷，审批通过后由 app 层执行。
ALTER TABLE ivs.approval_task ADD COLUMN payload_json text;
