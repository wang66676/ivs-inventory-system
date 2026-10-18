import { http } from './http'
import type { GrossProfitReport, MonthlyReport, StockFundReport } from '../types/report'

export const reportApi = {
  monthly: (params: { from: string; to: string }) =>
    http.get<MonthlyReport>('/ivs/reports/monthly', { params }).then((r) => r.data),
  grossProfit: (params: { from: string; to: string }) =>
    http.get<GrossProfitReport>('/ivs/reports/gross-profit', { params }).then((r) => r.data),
  stockFunds: (params?: { warehouseId?: string }) =>
    http.get<StockFundReport>('/ivs/reports/stock-funds', { params }).then((r) => r.data),
}
