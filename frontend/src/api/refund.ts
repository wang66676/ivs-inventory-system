import { http } from './http'
import type { RefundOrder } from '../types/refund'

export const refundApi = {
  list: () => http.get<RefundOrder[]>('/ivs/refunds').then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<RefundOrder>('/ivs/refunds', body).then((r) => r.data),
  execute: (id: string, body: { success: boolean; failureReason?: string; remark?: string }) =>
    http.post<RefundOrder>(`/ivs/refunds/${id}/execute`, body).then((r) => r.data),
  manualComplete: (id: string, remark?: string) =>
    http.post<RefundOrder>(`/ivs/refunds/${id}/manual-complete`, { remark }).then((r) => r.data),
  cancel: (id: string) => http.post<RefundOrder>(`/ivs/refunds/${id}/cancel`, {}).then((r) => r.data),
}
