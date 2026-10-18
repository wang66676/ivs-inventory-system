import { http } from './http'
import type { SystemSetting } from '../types/setting'
export const settingApi = {
  list: () => http.get<SystemSetting[]>('/ivs/system-settings').then((r) => r.data),
  update: (key: string, value: string) =>
    http.put<SystemSetting>(`/ivs/system-settings/${encodeURIComponent(key)}`, { value }).then((r) => r.data),
}
