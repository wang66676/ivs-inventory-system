import { http } from './http'
import type { Stocktake } from '../types/stocktake'

export const stocktakeApi = {
  list: (params?: { warehouseId?: string; status?: string }) =>
    http.get<Stocktake[]>('/ivs/stocktakes', { params }).then((r) => r.data),
  get: (id: string) => http.get<Stocktake>(`/ivs/stocktakes/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Stocktake>('/ivs/stocktakes', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<Stocktake>(`/ivs/stocktakes/${id}`, body).then((r) => r.data),
  post: (id: string) => http.post<Stocktake>(`/ivs/stocktakes/${id}/post`, {}).then((r) => r.data),
  cancel: (id: string) => http.post<Stocktake>(`/ivs/stocktakes/${id}/cancel`, {}).then((r) => r.data),
}
