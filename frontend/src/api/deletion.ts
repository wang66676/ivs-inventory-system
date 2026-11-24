import { http } from './http'
import type { DeletionBusinessType, DeletionRequest, DeletionRequestStatus } from '../types/deletion'

export const deletionApi = {
  list: (status?: DeletionRequestStatus) => http.get<DeletionRequest[]>('/ivs/delete-requests', {
    params: status ? { status } : undefined,
  }).then((response) => response.data),
  create: (body: { businessType: DeletionBusinessType; businessId: string; reason?: string }) =>
    http.post<DeletionRequest>('/ivs/delete-requests', body).then((response) => response.data),
  approve: (id: string, remark?: string) =>
    http.post<DeletionRequest>(`/ivs/delete-requests/${id}/approve`, { remark }).then((response) => response.data),
  reject: (id: string, remark: string) =>
    http.post<DeletionRequest>(`/ivs/delete-requests/${id}/reject`, { remark }).then((response) => response.data),
}
