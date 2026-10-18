package com.ivs.platform.organization.api;
import com.ivs.platform.organization.Company;
import com.ivs.platform.organization.Store;
import com.ivs.platform.organization.Team;
import java.time.Instant;
import java.util.UUID;
public record OrganizationView(UUID id, UUID companyId, UUID storeId, String code, String name, String status, Instant createdAt) {
    public static OrganizationView from(Company c){return new OrganizationView(c.getId(),null,null,c.getCode(),c.getName(),c.getStatus().name(),c.getCreatedAt());}
    public static OrganizationView from(Store s){return new OrganizationView(s.getId(),s.getCompanyId(),null,s.getCode(),s.getName(),s.getStatus().name(),s.getCreatedAt());}
    public static OrganizationView from(Team t){return new OrganizationView(t.getId(),t.getCompanyId(),t.getStoreId(),t.getCode(),t.getName(),t.getStatus().name(),t.getCreatedAt());}
}
