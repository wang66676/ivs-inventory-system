export interface PurchaseRedInvoice {
  id: string
  invoiceNo: string
  direction: string
  type: string
  invoiceType?: string
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
  pdfFileName?: string
  extractedText?: string
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

export interface PurchaseInvoicePdfExtraction {
  storageKey: string
  fileName: string
  rawText?: string
  invoiceNo?: string
  invoiceType?: string
  type?: 'BLUE' | 'RED'
  amount?: number
  taxAmount?: number
  taxRate?: number
  issuedAt?: string
  sellerName?: string
  sellerTaxNo?: string
  buyerName?: string
  buyerTaxNo?: string
  vinCandidates: string[]
  orderNoCandidates: string[]
  confidence: number
  textExtracted: boolean
  warning: string
}
