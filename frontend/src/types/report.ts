export interface MonthlyReportRow {
  period: string
  purchaseAmount: number
  purchaseReturnAmount: number
  netPurchaseAmount: number
  salesAmount: number
  salesReturnAmount: number
  netSalesAmount: number
  costAmount: number
  salesReturnCostAmount: number
  grossProfit: number
  rebateCost: number
  transferOutAmount: number
  transferInAmount: number
  stockValue: number
}

export interface MonthlyReport {
  from: string
  to: string
  rows: MonthlyReportRow[]
}

export interface GrossProfitRow {
  productId: string
  productNo?: string
  productName?: string
  salesQuantity: number
  salesAmount: number
  salesReturnAmount: number
  netSalesAmount: number
  costAmount: number
  salesReturnCostAmount: number
  netCostAmount: number
  grossProfit: number
  grossMargin: number
  rebateCost: number
  netProfit: number
  netMargin: number
}

export interface GrossProfitReport {
  from: string
  to: string
  rows: GrossProfitRow[]
}

export interface StockFundRow {
  warehouseId: string
  warehouseName?: string
  productId: string
  productNo?: string
  productName?: string
  categoryName?: string
  onHandQty: number
  lockedQty: number
  amount: number
  share: number
  ageDays: number
}

export interface StockFundReport {
  totalAmount: number
  totalOnHandQty: number
  rows: StockFundRow[]
}
