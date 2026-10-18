export interface LoginRequest { username: string; password: string }
export interface CurrentUser {
  userId: string
  username: string
  displayName: string
  dataScope: string
  roles: string[]
  permissions: string[]
  companyId?: string
  storeId?: string
  teamId?: string
}
export interface LoginResponse extends CurrentUser { token: string }

/** 修改自己的登录密码 */
export interface ChangePasswordRequest { currentPassword: string; newPassword: string }
