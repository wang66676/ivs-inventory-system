import { http } from './http'
import type {
  ClaimDifferenceRow, CostRateRow, Entitlement, FundLedgerReport, FundOffset, FundPool, FundTxn,
  OemClaim, Policy, PoolProgressRow, PreEntitlement, ReceivableAgingRow, UnitCostRow,
} from '../types/policy'

export const policyApi = {
  list: (params?: { status?: string }) => http.get<Policy[]>('/ivs/policies', { params }).then((r) => r.data),
  get: (id: string) => http.get<Policy>(`/ivs/policies/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Policy>('/ivs/policies', body).then((r) => r.data),
  publish: (id: string) => http.post<Policy>(`/ivs/policies/${id}/publish`, {}).then((r) => r.data),
  submitPublish: (id: string) => http.post<Policy>(`/ivs/policies/${id}/submit-publish`, {}).then((r) => r.data),
  cancel: (id: string) => http.post<Policy>(`/ivs/policies/${id}/cancel`, {}).then((r) => r.data),
  markConditionMet: (id: string) => http.post<Policy>(`/ivs/policies/${id}/condition-met`, {}).then((r) => r.data),

  preEntitlements: (params?: { orderId?: string }) =>
    http.get<PreEntitlement[]>('/ivs/pre-entitlements', { params }).then((r) => r.data),
  applyPreEntitlement: (body: Record<string, unknown>) =>
    http.post<PreEntitlement[]>('/ivs/pre-entitlements', body).then((r) => r.data),
  cancelPreEntitlement: (id: string) =>
    http.post<PreEntitlement>(`/ivs/pre-entitlements/${id}/cancel`, {}).then((r) => r.data),

  entitlements: (params?: { retailId?: string; supplierId?: string; periodCode?: string; status?: string }) =>
    http.get<Entitlement[]>('/ivs/entitlements', { params }).then((r) => r.data),
  generateEntitlements: (params: { orderId: string; retailId: string; retailDate: string; serialId?: string }) =>
    http.post<Entitlement[]>('/ivs/retail-entitlements/generate', params).then((r) => r.data),
  applyClaims: (body: { entitlementIds: string[]; merge: boolean; remark?: string }) =>
    http.post<OemClaim[]>('/ivs/entitlements/apply', body).then((r) => r.data),
  redeemEntitlement: (id: string) =>
    http.post<OemClaim[]>(`/ivs/entitlements/${id}/redeem`, {}).then((r) => r.data),

  claims: (params?: { supplierId?: string; periodCode?: string; status?: string }) =>
    http.get<OemClaim[]>('/ivs/oem-claims', { params }).then((r) => r.data),
  claim: (id: string) => http.get<OemClaim>(`/ivs/oem-claims/${id}`).then((r) => r.data),
  generateClaims: (params: { period: string; merge: boolean; remark?: string }) =>
    http.post<OemClaim[]>('/ivs/oem-claims/generate', {}, { params }).then((r) => r.data),
  submitClaim: (id: string) => http.post<OemClaim>(`/ivs/oem-claims/${id}/submit`, {}).then((r) => r.data),
  confirmClaim: (id: string, body: Record<string, unknown>) =>
    http.post<OemClaim>(`/ivs/oem-claims/${id}/confirm`, body).then((r) => r.data),
  disputeClaim: (id: string, body: { reason: string }) =>
    http.post<OemClaim>(`/ivs/oem-claims/${id}/dispute`, body).then((r) => r.data),
  resolveDifference: (claimId: string, differenceId: string, body: { remark?: string }) =>
    http.post<OemClaim>(`/ivs/oem-claims/${claimId}/differences/${differenceId}/resolve`, body).then((r) => r.data),
  closeClaim: (id: string) => http.post<OemClaim>(`/ivs/oem-claims/${id}/close`, {}).then((r) => r.data),

  fundPool: () => http.get<FundPool>('/ivs/rebate-fund-pool').then((r) => r.data),
  fundTxns: (params?: { supplierId?: string }) =>
    http.get<FundTxn[]>('/ivs/rebate-fund-pool/txns', { params }).then((r) => r.data),
  fundOffsets: (params?: { supplierId?: string }) =>
    http.get<FundOffset[]>('/ivs/rebate-fund-pool/offsets', { params }).then((r) => r.data),
  creditFund: (body: Record<string, unknown>) =>
    http.post<FundPool>('/ivs/rebate-fund-pool/credits', body).then((r) => r.data),

  reportAging: (params?: { supplierId?: string; periodCode?: string }) =>
    http.get<ReceivableAgingRow[]>('/ivs/policies/reports/receivable-aging', { params }).then((r) => r.data),
  reportPoolProgress: () =>
    http.get<PoolProgressRow[]>('/ivs/policies/reports/pool-progress').then((r) => r.data),
  reportFundLedger: (params?: { supplierId?: string }) =>
    http.get<FundLedgerReport>('/ivs/policies/reports/fund-ledger', { params }).then((r) => r.data),
  reportUnitCost: (params?: { periodCode?: string }) =>
    http.get<UnitCostRow[]>('/ivs/policies/reports/unit-cost', { params }).then((r) => r.data),
  reportCostRate: (params?: { periodCode?: string }) =>
    http.get<CostRateRow[]>('/ivs/policies/reports/cost-rate', { params }).then((r) => r.data),
  reportClaimDifferences: (params?: { supplierId?: string }) =>
    http.get<ClaimDifferenceRow[]>('/ivs/policies/reports/claim-differences', { params }).then((r) => r.data),
}
