import { http } from './http'
import type { RolePermission, RolePermissionCatalog } from '../types/role'

export const roleApi = {
  /** 角色权限页数据：角色清单 + 权限目录 + 当前授权 */
  async catalog(): Promise<RolePermissionCatalog> {
    const { data } = await http.get<RolePermissionCatalog>('/platform/roles')
    return data
  },

  /** 保存某角色的权限集合 */
  async update(roleCode: string, permissions: string[]): Promise<RolePermission> {
    const { data } = await http.put<RolePermission>(`/platform/roles/${roleCode}/permissions`, { permissions })
    return data
  },
}
