import { http } from './http'
import type { LeaseAutomationResult, LeaseContract, LeaseDueGenerationResult } from '../types/lease'

export const leaseApi = {
  list: () => http.get<LeaseContract[]>('/ivs/lease-contracts').then((r) => r.data),
  get: (id: string) => http.get<LeaseContract>(`/ivs/lease-contracts/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<LeaseContract>('/ivs/lease-contracts', body).then((r) => r.data),
  generateDue: (id: string, throughDate?: string) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/generate-due`, {}, { params: { throughDate } }).then((r) => r.data),
  generateDueBatch: (throughDate?: string) =>
    http.post<LeaseDueGenerationResult>('/ivs/lease-contracts/generate-due-batch', {}, { params: { throughDate } }).then((r) => r.data),
  runAutomation: (asOfDate?: string, reminderDays?: number) =>
    http.post<LeaseAutomationResult>('/ivs/lease-contracts/run-automation', {}, {
      params: { asOfDate, reminderDays },
    }).then((r) => r.data),
  end: (id: string) => http.post<LeaseContract>(`/ivs/lease-contracts/${id}/end`, {}).then((r) => r.data),
}
