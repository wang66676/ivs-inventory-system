import type { ApprovalTask } from '../types/approval'

const TASK_TYPE_PATHS: Record<string, string> = {
  SALES_PRICE: '/sales/orders',
  STOCKTAKE_POST: '/inventory/stocktakes',
  POLICY_PUBLISH: '/policy/policies',
  SUBSIDY_POOL_ADJUSTMENT: '/policy/subsidy-pools',
  FUND_POOL_CREDIT: '/policy/fund-pool',
  REFUND_APPROVAL: '/billing/refunds',
}

const BUSINESS_TYPE_PATHS: Record<string, string> = {
  SALES_ORDER: '/sales/orders',
  STOCKTAKE: '/inventory/stocktakes',
  POLICY: '/policy/policies',
  POLICY_POOL: '/policy/subsidy-pools',
  REBATE_FUND: '/policy/fund-pool',
  REFUND: '/billing/refunds',
}

export function approvalBusinessLocation(task: ApprovalTask) {
  const path = TASK_TYPE_PATHS[task.taskType] || BUSINESS_TYPE_PATHS[task.businessType]
  if (!path) return null
  return {
    path,
    query: {
      focus: task.businessId,
      businessNo: task.businessNo,
      taskNo: task.taskNo,
    },
  }
}