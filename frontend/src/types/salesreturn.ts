export type SalesReturnStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'

export interface SalesReturnLine {
  id: string
  productId: string
  productNo?: string
  productName?: string
  quantity: number
  unitPrice: number
  amount: number
  unitCost: number
  costAmount: number
  batchNo: string
  serialNos: string[]
}

export interface SalesReturn {
  id: string
  returnNo: string
  customerId: string
  customerName?: string
  deliveryId?: string
  warehouseId: string
  warehouseName?: string
  status: SalesReturnStatus
  totalAmount: number
  costAmount: number
  receivableAdjustmentAmount: number
  refundAmount: number
  reason?: string
  remark?: string
  postedAt?: string
  createdAt: string
  lines: SalesReturnLine[]
}
