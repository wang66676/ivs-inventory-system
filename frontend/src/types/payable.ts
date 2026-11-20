export type PayableStatus = 'OPEN' | 'PARTIALLY_PAID' | 'SETTLED'
export type PayablePaymentMethod = 'CASH' | 'THIRD_PARTY_STORAGE' | 'ACCEPTANCE_BILL' | 'OTHER'
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
  rebateCreditAmount: number
  netAmount: number
  invoiceMatchedAmount?: number
  uninvoicedAmount?: number
  paidAmount: number
  outstanding: number
  dueDate?: string
  lastPaymentDate?: string
  lastPaymentMethod?: PayablePaymentMethod
  status: PayableStatus
  remark?: string
  createdAt: string
}
export const PAYABLE_STATUS_LABELS: Record<PayableStatus, string> = {
  OPEN: '未付款', PARTIALLY_PAID: '部分付款', SETTLED: '已结清',
}

export const PAYABLE_PAYMENT_METHOD_LABELS: Record<PayablePaymentMethod, string> = {
  CASH: '现金',
  THIRD_PARTY_STORAGE: '三方库容',
  ACCEPTANCE_BILL: '承兑汇票',
  OTHER: '其他',
}
