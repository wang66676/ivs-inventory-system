export interface PermissionItem {
  code: string
  label: string
}

export interface PermissionGroup {
  group: string
  permissions: PermissionItem[]
}

export interface RolePermission {
  roleCode: string
  roleName: string
  permissions: string[]
}

export interface RolePermissionCatalog {
  roles: RolePermission[]
  groups: PermissionGroup[]
}
