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
  warehouseId: string
  startDate: string
  endDate: string
  monthlyFee: number
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
