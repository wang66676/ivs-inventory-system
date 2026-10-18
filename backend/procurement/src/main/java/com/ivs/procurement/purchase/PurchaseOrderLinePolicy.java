package com.ivs.procurement.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 采购明细与厂家返利政策的关联（一条采购明细可关联多项政策）。 */
@Entity
@Table(name = "purchase_order_line_policy", schema = "ivs")
public class PurchaseOrderLinePolicy {

    @Id
    private UUID id;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PurchaseOrderLinePolicy() {
    }

    public static PurchaseOrderLinePolicy create(UUID orderLineId, UUID policyId, int sortNo) {
        PurchaseOrderLinePolicy link = new PurchaseOrderLinePolicy();
        link.id = UUID.randomUUID();
        link.orderLineId = orderLineId;
        link.policyId = policyId;
        link.sortNo = sortNo;
        link.createdAt = Instant.now();
        return link;
    }

    public UUID getId() { return id; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getPolicyId() { return policyId; }
    public int getSortNo() { return sortNo; }
    public Instant getCreatedAt() { return createdAt; }
}
