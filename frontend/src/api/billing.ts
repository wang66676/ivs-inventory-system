import { http } from './http'
import type { InvoicePayableMatchResult, PurchaseInvoicePdfExtraction, PurchaseRedInvoice } from '../types/billing'

export const purchaseInvoiceApi = {
  list: (params?: { supplierId?: string }) =>
    http.get<PurchaseRedInvoice[]>('/ivs/purchase-invoices', { params }).then((r) => r.data),
  registerRed: (body: Record<string, unknown>) =>
    http.post<PurchaseRedInvoice>('/ivs/purchase-invoices/red', body).then((r) => r.data),
  registerBlue: (body: Record<string, unknown>) =>
    http.post<PurchaseRedInvoice>('/ivs/purchase-invoices/blue', body).then((r) => r.data),
  extractPdf: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<PurchaseInvoicePdfExtraction>('/ivs/purchase-invoices/pdf/extract', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    }).then((r) => r.data)
  },
  downloadPdf: (id: string) =>
    http.get<Blob>(`/ivs/purchase-invoices/${id}/pdf`, { responseType: 'blob' }).then((r) => r.data),
  autoMatch: (id: string) =>
    http.post<InvoicePayableMatchResult>(`/ivs/purchase-invoice-matches/${id}/auto`).then((r) => r.data),
  matches: (id: string) =>
    http.get<InvoicePayableMatchResult>(`/ivs/purchase-invoice-matches/${id}`).then((r) => r.data),
}
