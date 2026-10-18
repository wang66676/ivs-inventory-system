package com.ivs.platform.security.api;

import com.ivs.platform.security.RolePermissionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/roles")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    /** 角色权限页数据：角色清单 + 权限目录 + 当前授权。 */
    @GetMapping
    public RolePermissionCatalogView catalog() {
        return rolePermissionService.catalog();
    }

    @GetMapping("/{roleCode}/permissions")
    public RolePermissionView permissions(@PathVariable("roleCode") String roleCode) {
        return new RolePermissionView(roleCode, roleCode, rolePermissionService.permissions(roleCode));
    }

    @PutMapping("/{roleCode}/permissions")
    public RolePermissionView update(@PathVariable("roleCode") String roleCode,
                                     @Valid @RequestBody UpdateRolePermissionsCommand command) {
        return rolePermissionService.update(roleCode, command);
    }
}
