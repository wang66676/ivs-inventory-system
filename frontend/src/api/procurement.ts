import { http } from './http'
import type { PurchaseOrder, PurchaseReceipt } from '../types/procurement'

export const purchaseApi = {
  list: (params?: { keyword?: string; status?: string }) =>
    http.get<PurchaseOrder[]>('/ivs/purchase-orders', { params }).then((r) => r.data),
  get: (id: string) => http.get<PurchaseOrder>(`/ivs/purchase-orders/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<PurchaseOrder>('/ivs/purchase-orders', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<PurchaseOrder>(`/ivs/purchase-orders/${id}`, body).then((r) => r.data),
  approve: (id: string) => http.post<PurchaseOrder>(`/ivs/purchase-orders/${id}/approve`).then((r) => r.data),
  cancel: (id: string) => http.post<PurchaseOrder>(`/ivs/purchase-orders/${id}/cancel`).then((r) => r.data),
  receive: (id: string, body: Record<string, unknown>) =>
    http.post<PurchaseReceipt>(`/ivs/purchase-orders/${id}/receive`, body).then((r) => r.data),
  receipts: () => http.get<PurchaseReceipt[]>('/ivs/purchase-receipts').then((r) => r.data),
  updateReceiptCertificateDate: (receiptId: string, lineId: string, certificateApplicationDate: string | null) =>
    http.put<PurchaseReceipt>(`/ivs/purchase-receipts/${receiptId}/lines/${lineId}/certificate-application-date`,
      { certificateApplicationDate }).then((r) => r.data),
  updateReceiptWarehouse: (receiptId: string, lineId: string, warehouseId: string) =>
    http.put<PurchaseReceipt>(`/ivs/purchase-receipts/${receiptId}/lines/${lineId}/warehouse`,
      { warehouseId }).then((r) => r.data),
  returnReceipt: (receiptId: string, reason?: string) =>
    http.post<PurchaseReceipt>(`/ivs/purchase-receipts/${receiptId}/return`,
      { reason: reason || null }).then((r) => r.data),
}

export const inventoryApi = {
  balances: (params?: { warehouseId?: string; keyword?: string }) =>
    http.get('/ivs/inventory/balances', { params }).then((r) => r.data),
  ledger: (params?: { productId?: string; warehouseId?: string; limit?: number }) =>
    http.get('/ivs/inventory/ledger', { params }).then((r) => r.data),
  locks: (params?: { status?: string }) => http.get('/ivs/inventory/locks', { params }).then((r) => r.data),
  summary: () => http.get('/ivs/inventory/summary').then((r) => r.data),
  releaseLock: (lockId: string) => http.post(`/ivs/stocks/locks/${lockId}/release`).then((r) => r.data),
  reconcile: () => http.get('/ivs/stocks/reconciliation').then((r) => r.data),
}
