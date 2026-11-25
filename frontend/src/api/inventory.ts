import { http } from './http'
import type { AgeAnalysisItem, InventoryStatistics, InventorySummary, LedgerEntry, StockBalance, StockLock, VehicleOrderStock, VehicleSeriesInventoryDetail } from '../types/inventory'

export const inventoryApi = {
  balances: (params?: { warehouseId?: string; keyword?: string }) =>
    http.get<StockBalance[]>('/ivs/inventory/balances', { params }).then((r) => r.data),
  ledger: (params?: { productId?: string; warehouseId?: string; moveType?: string; limit?: number }) =>
    http.get<LedgerEntry[]>('/ivs/inventory/ledger', { params }).then((r) => r.data),
  vehicleOrderStock: (params?: { warehouseId?: string; keyword?: string }) =>
    http.get<VehicleOrderStock[]>('/ivs/inventory/vehicle-order-stock', { params }).then((r) => r.data),
  locks: (params?: { status?: string }) =>
    http.get<StockLock[]>('/ivs/inventory/locks', { params }).then((r) => r.data),
  summary: () => http.get<InventorySummary>('/ivs/inventory/summary').then((r) => r.data),
  statistics: (params: { from?: string; to?: string; businessScope?: string; warehouseType?: string }) =>
    http.get<InventoryStatistics>('/ivs/statistics/inventory', { params }).then((r) => r.data),
  vehicleSeriesDetail: (params: { series: string; config: string }) =>
    http.get<VehicleSeriesInventoryDetail[]>('/ivs/inventory/vehicle-series-detail', { params }).then((r) => r.data),
  ageAnalysis: () => http.get<AgeAnalysisItem[]>('/ivs/inventory/age-analysis').then((r) => r.data),
  releaseLock: (lockId: string) => http.post(`/ivs/stocks/locks/${lockId}/release`).then((r) => r.data),
  releaseExpiredLocks: () => http.post<number>('/ivs/stocks/locks/release-expired').then((r) => r.data),
  reconcile: () => http.get('/ivs/stocks/reconciliation').then((r) => r.data),
}
