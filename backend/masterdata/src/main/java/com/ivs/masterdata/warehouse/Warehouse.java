package com.ivs.masterdata.warehouse;

import com.ivs.masterdata.MasterStatus;
import com.ivs.masterdata.WarehouseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 仓库 / 门店仓 / 在途仓 / 资产池（二期电池租赁使用）。 */
@Entity
@Table(name = "warehouse", schema = "ivs")
public class Warehouse {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarehouseType type;

    @Column(length = 300)
    private String address;

    @Column(name = "owner_org_id")
    private UUID ownerOrgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Warehouse() {
    }

    public static Warehouse create(String code, String name, WarehouseType type, String address, UUID ownerOrgId) {
        Warehouse warehouse = new Warehouse();
        warehouse.id = UUID.randomUUID();
        warehouse.code = requireText(code, "仓库编码不能为空").toUpperCase();
        warehouse.name = requireText(name, "仓库名称不能为空");
        warehouse.type = type == null ? WarehouseType.MAIN : type;
        warehouse.address = normalize(address);
        warehouse.ownerOrgId = ownerOrgId;
        warehouse.status = MasterStatus.ACTIVE;
        warehouse.createdAt = Instant.now();
        warehouse.updatedAt = warehouse.createdAt;
        return warehouse;
    }

    public void update(String name, WarehouseType type, String address, UUID ownerOrgId) {
        this.name = requireText(name, "仓库名称不能为空");
        this.type = type == null ? this.type : type;
        this.address = normalize(address);
        this.ownerOrgId = ownerOrgId;
    }

    public void disable() { this.status = MasterStatus.DISABLED; }
    public void enable() { this.status = MasterStatus.ACTIVE; }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = MasterStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public WarehouseType getType() { return type; }
    public String getAddress() { return address; }
    public UUID getOwnerOrgId() { return ownerOrgId; }
    public MasterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}