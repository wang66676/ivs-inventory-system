import { http } from './http'
import type { PurchaseReturn } from '../types/purchasereturn'

export const purchaseReturnApi = {
  list: (params?: { supplierId?: string; status?: string }) =>
    http.get<PurchaseReturn[]>('/ivs/purchase-returns', { params }).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<PurchaseReturn>('/ivs/purchase-returns', body).then((r) => r.data),
  post: (id: string) => http.post<PurchaseReturn>(`/ivs/purchase-returns/${id}/post`, {}).then((r) => r.data),
  cancel: (id: string) => http.post<PurchaseReturn>(`/ivs/purchase-returns/${id}/cancel`, {}).then((r) => r.data),
}
