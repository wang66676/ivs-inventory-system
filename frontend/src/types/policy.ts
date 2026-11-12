export type PolicyStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'EXPIRED'
export type LimitType = 'TIME_ONLY' | 'AMOUNT' | 'CONDITION' | 'AMOUNT_AND_CONDITION'
export type PreEntitlementStatus = 'PENDING_RETAIL' | 'READY' | 'APPLIED' | 'SETTLED' | 'CANCELLED'
export type ClaimStatus =
  | 'DRAFT' | 'SUBMITTED' | 'CONFIRMED' | 'PARTIALLY_CONFIRMED' | 'DISPUTED' | 'SETTLED' | 'CLOSED'

export interface SubsidyPool {
  policyId: string
  limitType: LimitType
  totalAmount?: number
  usedAmount: number
  remainingAmount?: number
  perUnitAmount?: number
  conditionDesc?: string
  conditionMet: boolean
  effectiveFrom?: string
  effectiveTo?: string
  status: string
}

export interface Policy {
  id: string
  policyNo: string
  name: string
  policyType: string
  categoryId?: string
  supplierId?: string
  benefitType: 'AMOUNT' | 'RATE' | 'LADDER'
  benefitValue: number
  maxAmount?: number
  effFrom: string
  effTo: string
  referenceDateType: string
  claimCycle: string
  claimCutoffDay: number
  priority: number
  stackingRule: string
  status: PolicyStatus
  remark?: string
  pool?: SubsidyPool
  createdAt: string
}

export interface PreEntitlement {
  id: string
  preNo: string
  orderId: string
  orderLineId?: string
  productId: string
  serialId?: string
  policyId: string
  policyName: string
  estimateAmount: number
  status: PreEntitlementStatus
  addedAfterOrder: boolean
  addReason?: string
  serialNos?: string
  orderNo?: string
  invoiceIssuedAt?: string
  retailDate?: string
  referenceDateType?: string
  referenceDate?: string
  createdAt: string
}

export interface Entitlement {
  id: string
  entitlementNo: string
  policyId: string
  supplierId?: string
  retailId?: string
  serialId?: string
  productId: string
  orderId?: string
  faceAmount: number
  claimedAmount: number
  confirmedAmount: number
  settledAmount: number
  periodCode: string
  status: string
  createdAt: string
  policyName?: string
  purchaseOrderId?: string
  purchaseOrderNo?: string
  invoiceIssuedAt?: string
}

export interface ClaimLine {
  id: string
  entitlementId: string
  entitlementNo?: string
  retailId?: string
  retailNo?: string
  retailDate?: string
  policyId?: string
  policyName?: string
  serialId?: string
  serialNo?: string
  purchaseOrderId?: string
  orderNo?: string
  amount: number
  confirmedAmount: number
  differenceAmount: number
  differenceReason?: string
  differenceDisposition?: string
  dispositionTime?: string
}

export interface ClaimDifference {
  id: string
  claimLineId: string
  entitlementId: string
  amount: number
  reason: string
  disposition: string
  status: string
  resolutionRemark?: string
  createdAt: string
  resolvedAt?: string
}

export interface OemClaim {
  id: string
  claimNo: string
  supplierId: string
  supplierName?: string
  policyId?: string
  policyName?: string
  periodCode: string
  settleCycle: string
  mergedClaim: boolean
  claimAmount: number
  confirmedAmount: number
  differenceAmount: number
  cutoffAt?: string
  status: ClaimStatus
  submittedAt?: string
  confirmedAt?: string
  settledAt?: string
  closedAt?: string
  invoiceId?: string
  invoiceNo?: string
  remark?: string
  createdAt: string
  lines: ClaimLine[]
  differences: ClaimDifference[]
}

export interface ReceivableAgingRow {
  supplierId?: string
  supplierName?: string
  policyId?: string
  policyName?: string
  periodCode: string
  entitlementCount: number
  faceAmount: number
  claimedAmount: number
  confirmedAmount: number
  settledAmount: number
  outstandingAmount: number
  ageDays: number
  bucket: string
}

export interface PoolProgressRow {
  policyId: string
  policyNo: string
  policyName: string
  limitType: string
  totalAmount?: number
  usedAmount: number
  remainingAmount?: number
  usageRate: number
  usageCount: number
  status: string
  effectiveFrom?: string
  effectiveTo?: string
}

export interface FundLedgerRow {
  supplierId: string
  supplierName?: string
  totalInAmount: number
  totalOffsetAmount: number
  balanceAmount: number
}

export interface FundTxn {
  id: string
  supplierId: string
  direction: 'IN' | 'OUT'
  txnType: string
  amount: number
  balanceAfter: number
  remainingAmount: number
  sourceType?: string
  sourceNo?: string
  offsetNo?: string
  purchaseOrderId?: string
  purchaseReceiptId?: string
  occurredAt: string
  remark?: string
}

export interface FundCreditOption {
  txnId: string
  sourceType?: string
  sourceNo?: string
  remainingAmount: number
  occurredAt: string
}

export interface FundOffsetPreview {
  availableBalance: number
  appliedAmount: number
  credits: FundCreditOption[]
}

export interface FundOffsetLine {
  fundTxnId: string
  sourceNo?: string
  amount: number
}

export interface FundOffset {
  id: string
  offsetNo: string
  supplierId: string
  purchaseOrderId?: string
  purchaseReceiptId?: string
  receiptNo?: string
  appliedAmount: number
  balanceAfter: number
  status: string
  createdAt: string
  lines: FundOffsetLine[]
}

export interface FundPool {
  poolCode: string
  poolName: string
  balanceAmount: number
  totalInAmount: number
  totalOffsetAmount: number
  allowPreOffset: boolean
  suppliers: {
    supplierId: string
    balanceAmount: number
    totalInAmount: number
    totalOffsetAmount: number
  }[]
}

export interface FundLedgerReport {
  pool: FundPool
  bySupplier: FundLedgerRow[]
  transactions: FundTxn[]
  offsets: FundOffset[]
}

export interface UnitCostRow {
  entitlementId: string
  entitlementNo?: string
  status?: string
  periodCode: string
  retailId?: string
  retailNo?: string
  serialId?: string
  serialNo?: string
  productNo?: string
  productName?: string
  policyId: string
  policyName?: string
  purchaseOrderId?: string
  purchaseOrderNo?: string
  invoiceIssuedAt?: string
  salePrice?: number
  purchaseCost?: number
  policyCost: number
  netPrice?: number
}

export interface CostRateRow {
  periodCode: string
  productId?: string
  productNo?: string
  productName?: string
  policyCost: number
  salesRevenue: number
  costRate: number
}

export interface ClaimDifferenceRow {
  id: string
  claimNo?: string
  supplierName?: string
  entitlementId: string
  entitlementNo?: string
  amount: number
  reason: string
  disposition: string
  status: string
  createdAt: string
  resolvedAt?: string
  ageDays: number
}

export const POLICY_TYPE_LABELS: Record<string, string> = {
  PHASE_SUBSIDY: '阶段性政策补贴',
  STORE_SUBSIDY: '建店补贴',
  ANNUAL_REBATE: '年度返利',
  FINANCE_SUBSIDY: '金融贴息',
  TRADE_IN_SUBSIDY: '置换补贴',
  MARKETING_REBATE: '广宣费报销',
  STOCK_VEHICLE_SUBSIDY: '库存车补贴',
  OTHER: '其他',
}

export const CYCLE_LABELS: Record<string, string> = {
  MONTHLY: '月度',
  QUARTERLY: '季度',
  ANNUAL: '年度',
  MIXED: '混合',
}

export const LIMIT_TYPE_LABELS: Record<string, string> = {
  TIME_ONLY: '仅时间限制',
  AMOUNT: '金额上限',
  CONDITION: '条件限制',
  AMOUNT_AND_CONDITION: '金额 + 条件',
}

export const PRE_STATUS_LABELS: Record<string, string> = {
  PENDING_RETAIL: '待实销',
  READY: '可申请',
  APPLIED: '已申请',
  SETTLED: '已兑现',
  CANCELLED: '已取消',
}

export const ENTITLEMENT_STATUS_LABELS: Record<string, string> = {
  PENDING_CLAIM: '待申请',
  CLAIMED: '已归集',
  SUBMITTED: '已申报',
  CONFIRMED: '厂家已确认',
  DIFFERENCE: '确认有差异',
  DISPUTED: '厂家争议',
  SETTLED: '已结算',
}

export const CLAIM_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  CONFIRMED: '厂家已确认',
  PARTIALLY_CONFIRMED: '部分确认',
  DISPUTED: '有争议',
  SETTLED: '已结算',
  CLOSED: '已关闭',
}

export const DISPOSITION_LABELS: Record<string, string> = {
  NEXT_CLAIM: '下一期补回',
  SELF_BORNE: '转自担费用',
  APPEAL: '申诉',
  RED_INVOICE: '开具红字发票',
}
