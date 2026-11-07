export type RefundStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

export interface RefundOrder {
  id: string
  refundNo: string
  customerId: string
  customerName: string
  amount: number
  method: string
  sourceType: string
  sourceNo?: string
  reason: string
  status: RefundStatus
  failureReason?: string
  executionRemark?: string
  retryCount: number
  manualResolved: boolean
  approvedAt?: string
  executedAt?: string
  createdAt: string
  updatedAt: string
}

export const REFUND_STATUS_LABELS: Record<RefundStatus, string> = {
  PENDING_APPROVAL: '待审批',
  APPROVED: '待执行',
  REJECTED: '已驳回',
  SUCCESS: '退款成功',
  FAILED: '执行失败',
  CANCELLED: '已取消',
}

export const REFUND_STATUS_COLORS: Record<RefundStatus, string> = {
  PENDING_APPROVAL: 'orange',
  APPROVED: 'blue',
  REJECTED: 'red',
  SUCCESS: 'green',
  FAILED: 'red',
  CANCELLED: 'default',
}
