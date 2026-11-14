import { http } from './http'
import type { BackupResult, SystemHealthView } from '../types/systemHealth'

export const systemHealthApi = {
  status: () => http.get<SystemHealthView>('/platform/system-health').then((r) => r.data),
  backup: () => http.post<BackupResult>('/platform/system-health/backup').then((r) => r.data),
}