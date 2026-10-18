import { http } from './http'
import type { SalesReturn } from '../types/salesreturn'

export const salesReturnApi = {
  list: (params?: { customerId?: string; status?: string }) =>
    http.get<SalesReturn[]>('/ivs/sales-returns', { params }).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<SalesReturn>('/ivs/sales-returns', body).then((r) => r.data),
  post: (id: string) => http.post<SalesReturn>(`/ivs/sales-returns/${id}/post`, {}).then((r) => r.data),
  cancel: (id: string) => http.post<SalesReturn>(`/ivs/sales-returns/${id}/cancel`, {}).then((r) => r.data),
}
