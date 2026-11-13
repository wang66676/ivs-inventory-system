export interface PurchaseRedInvoice {
  id: string
  invoiceNo: string
  direction: string
  type: string
  supplierId: string
  purchaseOrderId?: string
  serialNos?: string[]
  amount: number
  matchedAmount?: number
  unmatchedAmount?: number
  taxRate?: number
  taxAmount?: number
  status: string
  originalInvoiceNo?: string
  externalInvoiceNo?: string
  claimId?: string
  issuedAt?: string
  createdAt: string
  remark?: string
}

export interface InvoicePayableMatchLine {
  payableId: string
  payableNo?: string
  amount: number
}

export interface InvoicePayableMatchResult {
  invoiceId: string
  matchedNow: number
  matchedAmount: number
  unmatchedAmount: number
  lines: InvoicePayableMatchLine[]
}
