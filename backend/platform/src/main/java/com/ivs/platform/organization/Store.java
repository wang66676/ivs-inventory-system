package com.ivs.platform.organization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store", schema = "ivs")
public class Store {
    @Id private UUID id;
    @Column(name="company_id", nullable=false) private UUID companyId;
    @Column(nullable=false, length=60) private String code;
    @Column(nullable=false, length=150) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OrganizationStatus status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected Store() {}
    public static Store create(UUID companyId, String code, String name){Store o=new Store();o.id=UUID.randomUUID();o.companyId=companyId;o.code=code;o.name=name;o.status=OrganizationStatus.ACTIVE;o.createdAt=Instant.now();o.updatedAt=o.createdAt;return o;}
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();Instant n=Instant.now();if(createdAt==null)createdAt=n;updatedAt=n;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public void disable(){status=OrganizationStatus.DISABLED;} public void enable(){status=OrganizationStatus.ACTIVE;}
    public UUID getId(){return id;} public UUID getCompanyId(){return companyId;} public String getCode(){return code;} public String getName(){return name;} public OrganizationStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
}
