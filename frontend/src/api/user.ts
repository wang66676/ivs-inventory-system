import { http } from './http'
import type { CreatePlatformUserRequest, PlatformUser } from '../types/user'

export const userApi = {
  async list(): Promise<PlatformUser[]> {
    const { data } = await http.get<PlatformUser[]>('/platform/users')
    return data
  },

  async create(command: CreatePlatformUserRequest): Promise<PlatformUser> {
    const { data } = await http.post<PlatformUser>('/platform/users', command)
    return data
  },

  async assignRoles(userId: string, roles: string[]): Promise<PlatformUser> {
    const { data } = await http.put<PlatformUser>(`/platform/users/${userId}/roles`, { roles })
    return data
  },

  async enable(userId: string): Promise<PlatformUser> {
    const { data } = await http.post<PlatformUser>(`/platform/users/${userId}/enable`)
    return data
  },

  /** 管理员为用户重置密码（该用户全部登录会话立即失效） */
  async resetPassword(userId: string, password: string): Promise<PlatformUser> {
    const { data } = await http.post<PlatformUser>(`/platform/users/${userId}/reset-password`, { password })
    return data
  },

  async disable(userId: string): Promise<PlatformUser> {
    const { data } = await http.post<PlatformUser>(`/platform/users/${userId}/disable`)
    return data
  },
}
