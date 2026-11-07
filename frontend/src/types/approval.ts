export type ApprovalMetricType = 'ALWAYS' | 'NONE' | 'AMOUNT' | 'DISCOUNT_PERCENT'

export interface ApprovalRule {
  ruleCode: string
  ruleName: string
  scenario: string
  enabled: boolean
  metricType: ApprovalMetricType
  metricLabel: string
  thresholdValue: number
  approvalPermission: string
  description?: string
  updatedAt: string
}

export interface ApprovalRuleUpdate {
  enabled: boolean
  metricType: ApprovalMetricType
  thresholdValue: number
  approvalPermission: string
  description?: string
}

export type ApprovalTaskStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface ApprovalTask {
  id: string
  taskNo: string
  taskType: string
  businessType: string
  businessId: string
  businessNo: string
  title: string
  summary?: string
  amount?: number
  approvalPermission: string
  status: ApprovalTaskStatus
  applicantName: string
  decisionByName?: string
  decisionReason?: string
  decidedAt?: string
  createdAt: string
  canDecide: boolean
}

export interface NotificationItem {
  id: string
  category: string
  title: string
  content: string
  businessType?: string
  businessId?: string
  read: boolean
  createdAt: string
}
