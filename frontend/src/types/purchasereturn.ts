export type PurchaseReturnStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'

export interface PurchaseReturnLine {
  id: string
  productId: string
  productNo?: string
  productName?: string
  quantity: number
  unitPrice: number
  amount: number
  batchNo: string
  serialNos: string[]
}

export interface PurchaseReturn {
  id: string
  returnNo: string
  supplierId: string
  supplierName?: string
  warehouseId: string
  warehouseName?: string
  status: PurchaseReturnStatus
  totalAmount: number
  reason?: string
  remark?: string
  postedAt?: string
  createdAt: string
  lines: PurchaseReturnLine[]
}
