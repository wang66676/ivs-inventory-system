export type PurchaseOrderStatus = 'DRAFT' | 'APPROVED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED'
export type ReceiptStatus = 'PENDING' | 'POSTED'

export interface PurchasePolicyRef {
  id: string
  policyNo: string
  name: string
}

export interface PurchaseOrderLine {
  id: string
  lineNo: number
  productId: string
  productNo: string
  productName: string
  series?: string
  configCode?: string
  categoryCode: string
  trackingMode: 'QUANTITY' | 'SERIAL'
  quantity: number
  receivedQuantity: number
  remainingQuantity: number
  unitPrice: number
  taxRate?: number
  amount: number
  remark?: string
  policies: PurchasePolicyRef[]
}

export interface PurchaseOrder {
  id: string
  orderNo: string
  externalOrderNo?: string
  supplierId: string
  supplierName: string
  supplierType: string
  warehouseId?: string
  warehouseName?: string
  status: PurchaseOrderStatus
  totalAmount: number
  expectedDate?: string
  remark?: string
  createdAt: string
  lines: PurchaseOrderLine[]
}

export interface PurchaseReceiptLine {
  id: string
  productId: string
  productNo: string
  productName: string
  quantity: number
  unitPrice: number
  batchNo?: string
  serialNos: string[]
}

export interface PurchaseReceipt {
  id: string
  receiptNo: string
  orderId: string
  orderNo: string
  supplierId: string
  supplierName: string
  warehouseId?: string
  warehouseName?: string
  status: ReceiptStatus
  receivedAt: string
  amount: number
  remark?: string
  lines: PurchaseReceiptLine[]
}

export const PURCHASE_STATUS_LABELS: Record<PurchaseOrderStatus, string> = {
  DRAFT: '草稿',
  APPROVED: '已审核',
  PARTIALLY_RECEIVED: '部分收货',
  RECEIVED: '已收货',
  CANCELLED: '已取消',
}

export const PURCHASE_STATUS_COLORS: Record<PurchaseOrderStatus, string> = {
  DRAFT: 'default',
  APPROVED: 'blue',
  PARTIALLY_RECEIVED: 'orange',
  RECEIVED: 'green',
  CANCELLED: 'red',
}

