export type StockTransferStatus = 'DRAFT' | 'APPROVED' | 'IN_TRANSIT' | 'RECEIVED' | 'CANCELLED'

export interface StockTransferLine {
  id: string
  productId: string
  batchNo: string
  quantity: number
  receivedQuantity: number
  differenceQuantity: number
  unitCost: number
  differenceReason?: string
}

export interface StockTransfer {
  id: string
  transferNo: string
  fromWarehouseId: string
  toWarehouseId: string
  status: StockTransferStatus
  lineCount: number
  totalQuantity: number
  receivedQuantity: number
  differenceQuantity: number
  remark?: string
  approvedBy?: string
  approvedAt?: string
  dispatchedBy?: string
  dispatchedAt?: string
  receivedBy?: string
  receivedAt?: string
  cancelledAt?: string
  createdBy?: string
  createdAt: string
  lines: StockTransferLine[]
}

export const TRANSFER_STATUS_LABELS: Record<StockTransferStatus, string> = {
  DRAFT: '草稿',
  APPROVED: '已审核',
  IN_TRANSIT: '在途',
  RECEIVED: '已签收',
  CANCELLED: '已取消',
}

export const TRANSFER_STATUS_COLORS: Record<StockTransferStatus, string> = {
  DRAFT: 'default',
  APPROVED: 'blue',
  IN_TRANSIT: 'orange',
  RECEIVED: 'green',
  CANCELLED: 'red',
}
