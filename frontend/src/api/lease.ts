import { http } from './http'
import type { LeaseContract } from '../types/lease'

export const leaseApi = {
  list: () => http.get<LeaseContract[]>('/ivs/lease-contracts').then((r) => r.data),
  get: (id: string) => http.get<LeaseContract>(`/ivs/lease-contracts/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<LeaseContract>('/ivs/lease-contracts', body).then((r) => r.data),
  generateDue: (id: string, throughDate?: string) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/generate-due`, {}, { params: { throughDate } }).then((r) => r.data),
  end: (id: string) => http.post<LeaseContract>(`/ivs/lease-contracts/${id}/end`, {}).then((r) => r.data),
}
