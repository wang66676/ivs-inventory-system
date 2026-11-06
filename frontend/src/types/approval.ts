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
