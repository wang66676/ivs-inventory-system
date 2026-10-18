import { http } from './http'
import type { StockTransfer } from '../types/stocktransfer'

export const transferApi = {
  list: (params?: { warehouseId?: string; status?: string }) =>
    http.get<StockTransfer[]>('/ivs/transfers', { params }).then((r) => r.data),
  get: (id: string) => http.get<StockTransfer>(`/ivs/transfers/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<StockTransfer>('/ivs/transfers', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<StockTransfer>(`/ivs/transfers/${id}`, body).then((r) => r.data),
  approve: (id: string) => http.post<StockTransfer>(`/ivs/transfers/${id}/approve`, {}).then((r) => r.data),
  dispatch: (id: string) => http.post<StockTransfer>(`/ivs/transfers/${id}/dispatch`, {}).then((r) => r.data),
  receive: (id: string, body: Record<string, unknown>) =>
    http.post<StockTransfer>(`/ivs/transfers/${id}/receive`, body).then((r) => r.data),
  cancel: (id: string) => http.post<StockTransfer>(`/ivs/transfers/${id}/cancel`, {}).then((r) => r.data),
}
