export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_DELIVERED' | 'DELIVERED' | 'CLOSED' | 'CANCELLED'
export type ReceivableStatus = 'OPEN' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'OVERDUE'
export type PaymentMethod = 'BANK_TRANSFER' | 'CASH' | 'ACCEPTANCE_BILL' | 'OTHER'
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'VOID' | 'RED_FLUSHED'
export type InvoiceType = 'BLUE' | 'RED'

export interface SalesOrderLine {
  id: string
  lineNo: number
  productId: string
  productNo: string
  productName: string
  categoryCode: string
  categoryName?: string
  trackingMode: 'QUANTITY' | 'SERIAL'
  quantity: number
  deliveredQuantity: number
  remainingQuantity: number
  unitPrice: number
  minPrice?: number
  taxRate?: number
  amount: number
  batteryBoxManufacturer?: string
  batteryBoxModel?: string
  batteryBoxUnitPrice?: number
  batteryPackManufacturer?: string
  batteryEnergyKwh?: number
  batteryPackUnitPrice?: number
  belowMinPrice: boolean
  serialIds: string[]
  remainingSerialIds: string[]
  remark?: string
}

export interface SalesOrder {
  id: string
  orderNo: string
  externalOrderNo?: string
  customerId: string
  customerName: string
  customerType: string
  warehouseId?: string
  warehouseName?: string
  status: SalesOrderStatus
  totalAmount: number
  priceApprovalRequired: boolean
  priceApproved: boolean
  lockedAt?: string
  remark?: string
  createdAt: string
  lines: SalesOrderLine[]
}

export interface SalesDeliveryLine {
  id: string
  productId: string
  productNo: string
  productName: string
  quantity: number
  unitPrice: number
  costAmount: number
  serialIds: string[]
}

export interface SalesDelivery {
  id: string
  deliveryNo: string
  orderId: string
  orderNo: string
  customerId: string
  customerName: string
  warehouseId: string
  warehouseName: string
  status: string
  deliveredAt: string
  amount: number
  costAmount: number
  grossProfit: number
  remark?: string
  lines: SalesDeliveryLine[]
}

export interface Receivable {
  id: string
  receivableNo: string
  customerId: string
  customerName: string
  orderId?: string
  orderNo?: string
  deliveryId?: string
  amount: number
  receivedAmount: number
  outstanding: number
  status: ReceivableStatus
  dueDate?: string
  createdAt: string
}

export interface ReceiptAllocation {
  receivableId: string
  receivableNo: string
  amount: number
}

export interface Receipt {
  id: string
  receiptNo: string
  customerId: string
  customerName: string
  amount: number
  method: PaymentMethod
  receivedAt: string
  remark?: string
  allocations: ReceiptAllocation[]
}

export interface AgingBucket {
  label: string
  count: number
  amount: number
}

export interface Invoice {
  id: string
  invoiceNo: string
  direction: 'OUT' | 'IN'
  type: InvoiceType
  orderId?: string
  orderNo?: string
  deliveryId?: string
  customerId: string
  customerName: string
  amount: number
  taxRate?: number
  taxAmount?: number
  status: InvoiceStatus
  originalInvoiceId?: string
  externalInvoiceNo?: string
  issuedAt?: string
  createdAt: string
  remark?: string
}

export const SALES_STATUS_LABELS: Record<SalesOrderStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  PARTIALLY_DELIVERED: '部分出库',
  DELIVERED: '已出库',
  CLOSED: '已关闭（款货两清）',
  CANCELLED: '已取消',
}

export const SALES_STATUS_COLORS: Record<SalesOrderStatus, string> = {
  DRAFT: 'default',
  CONFIRMED: 'blue',
  PARTIALLY_DELIVERED: 'orange',
  DELIVERED: 'green',
  CLOSED: 'purple',
  CANCELLED: 'red',
}

export const RECEIVABLE_STATUS_LABELS: Record<ReceivableStatus, string> = {
  OPEN: '未收',
  PARTIALLY_SETTLED: '部分核销',
  SETTLED: '已结清',
  OVERDUE: '逾期',
}

export const RECEIVABLE_STATUS_COLORS: Record<ReceivableStatus, string> = {
  OPEN: 'orange',
  PARTIALLY_SETTLED: 'blue',
  SETTLED: 'green',
  OVERDUE: 'red',
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  BANK_TRANSFER: '银行转账',
  CASH: '现金',
  ACCEPTANCE_BILL: '承兑汇票',
  OTHER: '其他',
}

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: '草稿',
  ISSUED: '已开具',
  VOID: '已作废',
  RED_FLUSHED: '已红冲',
}

export const INVOICE_STATUS_COLORS: Record<InvoiceStatus, string> = {
  DRAFT: 'default',
  ISSUED: 'green',
  VOID: 'red',
  RED_FLUSHED: 'orange',
}

export const INVOICE_TYPE_LABELS: Record<InvoiceType, string> = {
  BLUE: '蓝票',
  RED: '红字发票',
}
