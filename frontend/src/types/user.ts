export type DataScope = 'SELF' | 'TEAM' | 'STORE' | 'COMPANY' | 'ALL'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface PlatformUser {
  id: string
  username: string
  displayName: string
  dataScope: DataScope
  companyId?: string
  storeId?: string
  teamId?: string
  status: UserStatus
  roles: string[]
  createdAt: string
}

export interface CreatePlatformUserRequest {
  username: string
  displayName: string
  password: string
  dataScope: DataScope
  companyId?: string
  storeId?: string
  teamId?: string
  roles: string[]
}

export const roleLabels: Record<string, string> = {
  ADMIN: '系统管理员', SALES: '销售', DMS_OPERATOR: '车辆运营', FINANCE: '财务',
}
export const dataScopeLabels: Record<DataScope, string> = {
  SELF: '仅本人', TEAM: '本团队', STORE: '本门店', COMPANY: '本公司', ALL: '全部数据',
}
