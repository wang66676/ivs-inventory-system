import { http } from './http'
import type { Payable } from '../types/payable'
export const payableApi = {
  list: (params?: { supplierId?: string }) => http.get<Payable[]>('/ivs/payables', { params }).then((r) => r.data),
  pay: (id: string, body: Record<string, unknown>) => http.post<Payable>(`/ivs/payables/${id}/pay`, body).then((r) => r.data),
}
