import { expect, test } from '@playwright/test'
import { approvalBusinessLocation } from '../src/utils/approvalNavigation'

const base = (taskType: string, businessType: string) => ({
  id: 'task-id',
  taskNo: 'AP-001',
  taskType,
  businessType,
  businessId: 'business-id',
  businessNo: 'BIZ-001',
  title: '测试审批',
  approvalPermission: 'USER_MANAGE',
  status: 'PENDING' as const,
  applicantName: '测试用户',
  createdAt: new Date().toISOString(),
  canDecide: true,
})

test('审批待办覆盖全部已实现业务类型', () => {
  expect(approvalBusinessLocation(base('SALES_PRICE', 'SALES_ORDER'))?.path).toBe('/sales/orders')
  expect(approvalBusinessLocation(base('STOCKTAKE_POST', 'STOCKTAKE'))?.path).toBe('/inventory/stocktakes')
  expect(approvalBusinessLocation(base('POLICY_PUBLISH', 'POLICY'))?.path).toBe('/policy/policies')
  expect(approvalBusinessLocation(base('SUBSIDY_POOL_ADJUSTMENT', 'POLICY_POOL'))?.path).toBe('/policy/subsidy-pools')
  expect(approvalBusinessLocation(base('FUND_POOL_CREDIT', 'REBATE_FUND'))?.path).toBe('/policy/fund-pool')
  expect(approvalBusinessLocation(base('REFUND_APPROVAL', 'REFUND'))?.path).toBe('/billing/refunds')
})