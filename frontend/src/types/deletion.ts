export type DeletionRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type DeletionBusinessType = 'SALES_ORDER' | 'PRE_ENTITLEMENT'

export interface DeletionRequest {
  id: string
  businessType: DeletionBusinessType
  businessTypeLabel: string
  businessId: string
  businessNo: string
  moduleName: string
  reason?: string
  requesterId: string
  requesterName: string
  status: DeletionRequestStatus
  reviewerName?: string
  reviewRemark?: string
  requestedAt: string
  reviewedAt?: string
}

export const DELETION_STATUS_LABELS: Record<DeletionRequestStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
