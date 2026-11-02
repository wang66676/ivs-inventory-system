-- 每条 VIN 级预享受项最多生成一条正式权益，防止并发重复生成。
CREATE UNIQUE INDEX uk_entitlement_pre
    ON ivs.policy_entitlement (pre_entitlement_id);
