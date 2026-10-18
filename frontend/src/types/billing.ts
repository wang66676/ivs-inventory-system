export interface PurchaseRedInvoice {
  id: string
  invoiceNo: string
  direction: string
  type: string
  supplierId: string
  amount: number
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
