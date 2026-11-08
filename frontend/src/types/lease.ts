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
  ownershipAtEnd: LeaseOwnershipAtEnd
  status: LeaseStatus
  remark?: string
  createdAt: string
  schedules: LeaseSchedule[]
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
