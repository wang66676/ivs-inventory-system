import { http } from './http'
import type { PurchaseRedInvoice } from '../types/billing'

export const purchaseInvoiceApi = {
  list: (params?: { supplierId?: string }) =>
    http.get<PurchaseRedInvoice[]>('/ivs/purchase-invoices', { params }).then((r) => r.data),
  registerRed: (body: Record<string, unknown>) =>
    http.post<PurchaseRedInvoice>('/ivs/purchase-invoices/red', body).then((r) => r.data),
}
