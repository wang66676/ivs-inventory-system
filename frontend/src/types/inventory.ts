export type StockDirection = 'IN' | 'OUT'
export type StockLockStatus = 'ACTIVE' | 'RELEASED' | 'CONSUMED' | 'EXPIRED'

export interface StockBalance {
  id: string
  productId: string
  productNo: string
  productName: string
  categoryCode: string
  trackingMode: string
  warehouseId: string
  warehouseName: string
  batchNo: string
  onHandQty: number
  lockedQty: number
  availableQty: number
  unitCost: number
  amount: number
  updatedAt: string
  purchaseOrderId?: string
  purchaseOrderNo?: string
}

export interface LedgerEntry {
  id: string
  productId: string
  productNo: string
  productName: string
  serialId?: string
  serialNo?: string
  warehouseId: string
  warehouseName: string
  moveType: string
  direction: StockDirection
  quantity: number
  unitCost: number
  amount: number
  balanceAfter: number
  bizType?: string
  bizNo?: string
  occurredAt: string
  remark?: string
}

export interface StockLock {
  id: string
  productId: string
  productNo: string
  productName: string
  serialId?: string
  serialNo?: string
  warehouseId: string
  warehouseName: string
  quantity: number
  status: StockLockStatus
  bizType?: string
  bizNo?: string
  expiresAt: string
  createdAt: string
}

export interface AgeBucket {
  label: string
  serialCount: number
  amount: number
}

export interface AgeAnalysisItem {
  serialId: string
  productId: string
  serialNo: string
  productNo: string
  productName: string
  categoryCode: string
  categoryName: string
  series?: string
  warehouseId?: string
  warehouseName?: string
  status: string
  inboundAt: string
  ageDays: number
  ageBucket: string
  purchasePrice: number
}

export interface InventorySummary {
  serialInStock: number
  vehicleInStockNotLocked: number
  batteryInStock: number
  quantityItems: number
  totalAmount: number
  totalLockedQty: number
  byCategory: { categoryName: string; itemCount: number; amount: number }[]
  byWarehouse: { warehouseName: string; itemCount: number; amount: number }[]
  ageBuckets: AgeBucket[]
  vehicleSeriesInventory: { series: string; unsoldVinCount: number; lockedVinCount: number }[]
  ledgerCount: number
  consistent: boolean
}

export const MOVE_TYPE_LABELS: Record<string, string> = {
  OPENING: '期初',
  PURCHASE_IN: '采购入库',
  SALE_OUT: '销售出库',
  TRANSFER_OUT: '调拨出库',
  TRANSFER_IN: '调拨入库',
  STOCKTAKE_ADJUST: '盘点调整',
  MANUAL_ADJUST: '手工调整',
  SALE_RETURN_IN: '销售退回',
  PURCHASE_RETURN_OUT: '采购退回',
  EXCHANGE_OUT: '政策兑换出库',
  SCRAP: '报废',
  LEASE_OUT: '租赁出库',
  LEASE_RETURN: '租赁归还',
}

export const LOCK_STATUS_LABELS: Record<StockLockStatus, string> = {
  ACTIVE: '已锁定',
  RELEASED: '已释放',
  CONSUMED: '已消耗',
  EXPIRED: '已超时',
}
