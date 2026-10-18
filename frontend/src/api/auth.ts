import { http } from './http'
import type { ChangePasswordRequest, CurrentUser, LoginRequest, LoginResponse } from '../types/auth'

export const authApi = {
  async login(command: LoginRequest): Promise<LoginResponse> {
    const { data } = await http.post<LoginResponse>('/platform/auth/login', command)
    return data
  },

  async me(): Promise<CurrentUser> {
    const { data } = await http.get<CurrentUser>('/platform/auth/me')
    return data
  },

  /** 修改自己的登录密码（成功后其它设备的登录会被强制下线） */
  async changePassword(command: ChangePasswordRequest): Promise<void> {
    await http.post('/platform/auth/change-password', command)
  },

  async logout(): Promise<void> {
    await http.post('/platform/auth/logout')
  },
}
