package com.ivs.platform.organization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "team", schema = "ivs")
public class Team {
    @Id private UUID id;
    @Column(name="company_id", nullable=false) private UUID companyId;
    @Column(name="store_id", nullable=false) private UUID storeId;
    @Column(nullable=false, length=60) private String code;
    @Column(nullable=false, length=150) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OrganizationStatus status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected Team() {}
    public static Team create(UUID companyId, UUID storeId, String code, String name){Team o=new Team();o.id=UUID.randomUUID();o.companyId=companyId;o.storeId=storeId;o.code=code;o.name=name;o.status=OrganizationStatus.ACTIVE;o.createdAt=Instant.now();o.updatedAt=o.createdAt;return o;}
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();Instant n=Instant.now();if(createdAt==null)createdAt=n;updatedAt=n;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public void disable(){status=OrganizationStatus.DISABLED;} public void enable(){status=OrganizationStatus.ACTIVE;}
    public UUID getId(){return id;} public UUID getCompanyId(){return companyId;} public UUID getStoreId(){return storeId;} public String getCode(){return code;} public String getName(){return name;} public OrganizationStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
}
