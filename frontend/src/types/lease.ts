export type LeaseStatus = 'ACTIVE' | 'ENDED' | 'CANCELLED'
export type LeaseOwnershipAtEnd = 'RETURN' | 'BUYOUT' | 'RENEW'

export interface LeaseSchedule {
  id: string
  periodNo: number
  dueDate: string
  amount: number
  receivableId?: string
  receivableNo?: string
  receivableStatus: string
  receivedAmount: number
  outstanding: number
}

export interface LeaseDepositTransaction {
  id: string
  type: 'COLLECT' | 'APPLY_RENT' | 'REFUND'
  amount: number
  transactionDate: string
  method?: string
  referenceNo?: string
  remark?: string
  createdAt: string
}

export interface LeaseSettlement {
  id: string
  settlementDate: string
  outstandingBefore: number
  depositApplied: number
  outstandingAfter: number
  depositRefundable: number
  reason?: string
  createdAt: string
}

export interface LeaseContract {
  id: string
  contractNo: string
  customerId: string
  customerName: string
  serialId: string
  serialNo: string
  productName: string
  serialStatus: string
  warehouseId: string
  startDate: string
  endDate: string
  monthlyFee: number
  purchaseCost: number
  monthlyDepreciation: number
  accumulatedDepreciation: number
  netValue: number
  depreciationMonths: number
  deposit: number
  depositReceived: number
  depositApplied: number
  depositRefunded: number
  depositAvailable: number
  ownershipAtEnd: LeaseOwnershipAtEnd
  status: LeaseStatus
  remark?: string
  createdAt: string
  schedules: LeaseSchedule[]
  depositTransactions: LeaseDepositTransaction[]
  settlement?: LeaseSettlement
  changes: LeaseContractChange[]
}

export interface LeaseContractChange {
  id: string
  type: 'RENEW'
  oldEndDate: string
  newEndDate: string
  oldMonthlyFee: number
  newMonthlyFee: number
  reason?: string
  createdAt: string
}

export interface LeaseDepositCommand {
  amount: number
  transactionDate: string
  method?: string
  remark?: string
}

export interface LeaseEndCommand {
  settlementDate: string
  applyDeposit: boolean
  reason?: string
}

export interface LeaseRenewCommand {
  newEndDate: string
  newMonthlyFee: number
  reason?: string
}

export type LeaseInspectionResult = 'NORMAL' | 'ATTENTION' | 'ABNORMAL'

export interface LeaseHealthRecord {
  id: string
  recordDate: string
  sohPercent: number
  cycleCount: number
  mileageKm: number
  inspectionResult: LeaseInspectionResult
  inspector?: string
  remark?: string
  createdAt: string
}

export interface LeaseHealthCommand {
  recordDate: string
  sohPercent: number
  cycleCount: number
  mileageKm: number
  inspectionResult: LeaseInspectionResult
  inspector?: string
  remark?: string
}

export interface LeaseAssetReportSummary {
  totalContracts: number
  activeContracts: number
  attentionAssets: number
  activeMonthlyRent: number
  generatedRent: number
  receivedRent: number
  outstandingRent: number
  depositAvailable: number
  purchaseCost: number
  accumulatedDepreciation: number
  netValue: number
}

export interface LeaseAssetReportRow {
  contractId: string
  contractNo: string
  customerName: string
  serialNo: string
  productName: string
  serialStatus: string
  status: LeaseStatus
  startDate: string
  endDate: string
  monthlyFee: number
  settlementDate?: string
  purchaseCost: number
  monthlyDepreciation: number
  accumulatedDepreciation: number
  netValue: number
  depreciationMonths: number
  generatedRent: number
  receivedRent: number
  outstandingRent: number
  depositExpected: number
  depositReceived: number
  depositApplied: number
  depositRefunded: number
  depositAvailable: number
  latestSohPercent?: number
  latestCycleCount?: number
  latestMileageKm?: number
  latestInspectionResult?: LeaseInspectionResult
  latestInspectionDate?: string
}

export interface LeaseAssetReport {
  generatedAt: string
  summary: LeaseAssetReportSummary
  rows: LeaseAssetReportRow[]
}

export type LeaseDispositionType = 'SECOND_LIFE' | 'SCRAP'

export interface LeaseAssetDisposal {
  id: string
  disposalNo: string
  serialId: string
  contractId?: string
  dispositionType: LeaseDispositionType
  disposalDate: string
  sohPercent: number
  destination?: string
  reason: string
  remark?: string
  createdAt: string
}

export interface RetireAssetCommand {
  sohPercent: number
  dispositionType: LeaseDispositionType
  disposalDate: string
  destination?: string
  reason: string
  remark?: string
}

export interface LeaseDueGenerationResult {
  throughDate: string
  activeContracts: number
  generatedReceivables: number
}

export interface LeaseAutomationResult {
  asOfDate: string
  reminderDays: number
  activeContracts: number
  generatedReceivables: number
  dueSoonReminders: number
  overdueReminders: number
}
