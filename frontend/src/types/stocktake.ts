export type StocktakeStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'POSTED' | 'CANCELLED'

export interface StocktakeLine {
  id: string
  productId: string
  batchNo: string
  bookQty: number
  actualQty: number
  differenceQty: number
  unitCost: number
  differenceReason?: string
}

export interface Stocktake {
  id: string
  stocktakeNo: string
  warehouseId: string
  status: StocktakeStatus
  lineCount: number
  differenceCount: number
  totalDifferenceQty: number
  remark?: string
  postedBy?: string
  postedAt?: string
  createdBy?: string
  createdAt: string
  lines: StocktakeLine[]
}

export const STOCKTAKE_STATUS_LABELS: Record<StocktakeStatus, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

export const STOCKTAKE_STATUS_COLORS: Record<StocktakeStatus, string> = {
  DRAFT: 'orange',
  PENDING_APPROVAL: 'gold',
  POSTED: 'green',
  CANCELLED: 'default',
}
