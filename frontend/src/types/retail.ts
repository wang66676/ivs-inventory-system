import type { ProductSerial } from './masterdata'

export type RetailStatus = 'DRAFT' | 'SUBMITTED' | 'VERIFIED' | 'REVERSED'

export interface RetailCandidate {
  serial: ProductSerial
  orderId?: string
  orderNo?: string
  purchaseOrderId?: string
  purchaseOrderNo?: string
  customerId?: string
  customerName?: string
}

export interface RetailRecord {
  id: string
  retailNo: string
  retailDate: string
  invoiceIssuedAt: string
  registerDate?: string
  deliveryDate?: string
  serialId: string
  serialNo: string
  productId: string
  productNo?: string
  productName?: string
  categoryCode?: string
  customerId?: string
  customerName?: string
  orderId?: string
  userName: string
  userMobile: string
  plateNo?: string
  salePrice?: number
  status: RetailStatus
  verifiedAt?: string
  remark?: string
  createdAt: string
}

export const RETAIL_STATUS_LABELS: Record<RetailStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待复核',
  VERIFIED: '已确认',
  REVERSED: '已冲正',
}

export const RETAIL_STATUS_COLORS: Record<RetailStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'orange',
  VERIFIED: 'green',
  REVERSED: 'red',
}
