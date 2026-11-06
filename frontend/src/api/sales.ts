import { http } from './http'
import type { AgingBucket, InstallmentPlan, Invoice, Receipt, Receivable, SalesDelivery, SalesOrder } from '../types/sales'

export const salesApi = {
  orders: (params?: { keyword?: string; status?: string; customerId?: string }) =>
    http.get<SalesOrder[]>('/ivs/sales-orders', { params }).then((r) => r.data),
  order: (id: string) => http.get<SalesOrder>(`/ivs/sales-orders/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<SalesOrder>('/ivs/sales-orders', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<SalesOrder>(`/ivs/sales-orders/${id}`, body).then((r) => r.data),
  updatePrices: (id: string, lines: { orderLineId: string; unitPrice: number }[]) =>
    http.put<SalesOrder>(`/ivs/sales-orders/${id}/prices`, { lines }).then((r) => r.data),
  approvePrice: (id: string) => http.post<SalesOrder>(`/ivs/sales-orders/${id}/approve-price`).then((r) => r.data),
  confirm: (id: string) => http.post<SalesOrder>(`/ivs/sales-orders/${id}/confirm`).then((r) => r.data),
  lock: (id: string) => http.post<SalesOrder>(`/ivs/sales-orders/${id}/lock`, {}).then((r) => r.data),
  cancel: (id: string) => http.post<SalesOrder>(`/ivs/sales-orders/${id}/cancel`).then((r) => r.data),
  deleteOrder: (id: string) => http.delete(`/ivs/sales-orders/${id}`),
  deliver: (id: string, body: Record<string, unknown>) =>
    http.post<SalesDelivery>(`/ivs/sales-orders/${id}/deliver`, body).then((r) => r.data),
  deliveries: () => http.get<SalesDelivery[]>('/ivs/sales-deliveries').then((r) => r.data),
  receivables: (params?: { customerId?: string }) =>
    http.get<Receivable[]>('/ivs/receivables', { params }).then((r) => r.data),
  aging: () => http.get<AgingBucket[]>('/ivs/receivables/aging').then((r) => r.data),
  installmentPlans: () => http.get<InstallmentPlan[]>('/ivs/installment-plans').then((r) => r.data),
  installmentPlan: (id: string) => http.get<InstallmentPlan>(`/ivs/installment-plans/${id}`).then((r) => r.data),
  receipts: () => http.get<Receipt[]>('/ivs/receipts').then((r) => r.data),
  createReceipt: (body: Record<string, unknown>) => http.post<Receipt>('/ivs/receipts', body).then((r) => r.data),
}

export const invoiceApi = {
  list: (params?: { orderId?: string; customerId?: string }) =>
    http.get<Invoice[]>('/ivs/sales-invoices', { params }).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Invoice>('/ivs/sales-invoices', body).then((r) => r.data),
  issue: (id: string, body: Record<string, unknown>) =>
    http.post<Invoice>(`/ivs/sales-invoices/${id}/issue`, body).then((r) => r.data),
  void: (id: string) => http.post<Invoice>(`/ivs/sales-invoices/${id}/void`).then((r) => r.data),
  redFlush: (id: string) => http.post<Invoice>(`/ivs/sales-invoices/${id}/red-flush`).then((r) => r.data),
}
