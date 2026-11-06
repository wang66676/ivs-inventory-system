import { http } from './http'
import type { RetailCandidate, RetailPendingItem, RetailRecord } from '../types/retail'

export const retailApi = {
  list: (params?: { status?: string; from?: string; to?: string }) =>
    http.get<RetailRecord[]>('/ivs/retails', { params }).then((r) => r.data),
  candidates: () => http.get<RetailCandidate[]>('/ivs/retail-candidates').then((r) => r.data),
  pending: () => http.get<RetailPendingItem[]>('/ivs/retail-pending').then((r) => r.data),
  get: (id: string) => http.get<RetailRecord>(`/ivs/retails/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<RetailRecord>('/ivs/retails', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<RetailRecord>(`/ivs/retails/${id}`, body).then((r) => r.data),
  submit: (id: string) => http.post<RetailRecord>(`/ivs/retails/${id}/submit`, {}).then((r) => r.data),
  verify: (id: string) => http.post<RetailRecord>(`/ivs/retails/${id}/verify`, {}).then((r) => r.data),
  reverse: (id: string, body: { reason: string; newSerialId?: string }) =>
    http.post<RetailRecord>(`/ivs/retails/${id}/reverse`, body).then((r) => r.data),
}
