export type PayableStatus = 'OPEN' | 'PARTIALLY_PAID' | 'SETTLED'
export interface Payable {
  id: string
  payableNo: string
  supplierId: string
  supplierName?: string
  purchaseOrderId?: string
  purchaseReceiptId?: string
  orderNo?: string
  receiptNo?: string
  purchaseQuantity: number
  amount: number
  invoiceMatchedAmount?: number
  uninvoicedAmount?: number
  paidAmount: number
  outstanding: number
  dueDate?: string
  status: PayableStatus
  remark?: string
  createdAt: string
}
export const PAYABLE_STATUS_LABELS: Record<PayableStatus, string> = {
  OPEN: '未付款', PARTIALLY_PAID: '部分付款', SETTLED: '已结清',
}
