package com.ivs.platform.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "platform_user_role", schema = "ivs",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role_code"}))
public class UserRoleAssignment {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_code", nullable = false, length = 60)
    private String roleCode;

    protected UserRoleAssignment() {
    }

    public static UserRoleAssignment assign(UUID userId, String roleCode) {
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.id = UUID.randomUUID();
        assignment.userId = userId;
        assignment.roleCode = roleCode;
        return assignment;
    }

    public UUID getUserId() { return userId; }
    public String getRoleCode() { return roleCode; }
}
