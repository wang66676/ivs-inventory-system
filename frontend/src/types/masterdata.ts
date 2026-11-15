export type TrackingMode = 'QUANTITY' | 'SERIAL'
export type MasterStatus = 'ACTIVE' | 'DISABLED'
export type WarehouseType = 'MAIN' | 'STORE' | 'SECONDARY' | 'CUSTOMER' | 'TEMPORARY' | 'OTHER'
export type SupplierType = 'VEHICLE_OEM' | 'BATTERY_OEM' | 'FRAME_OEM' | 'OTHER'
export type CustomerType = 'INDIVIDUAL' | 'COMPANY' | 'FLEET' | 'SECONDARY' | 'OTHER'
export type OwnershipType = 'OWNED' | 'LEASE_TO_OWN' | 'LEASED'
export type SerialStatus =
  | 'PLANNED' | 'IN_TRANSIT' | 'ARRIVED' | 'IN_STOCK' | 'LOCKED'
  | 'SOLD' | 'DELIVERED' | 'RETURNED' | 'REPAIRING'
  | 'IN_LEASE' | 'RETIRED' | 'SECOND_LIFE' | 'SCRAPPED'

export interface AttrDef {
  id: string
  categoryId: string
  code: string
  name: string
  dataType: string
  required: boolean
  optionsJson?: string
  sortOrder: number
}

export interface Category {
  id: string
  code: string
  name: string
  trackingMode: TrackingMode
  unit: string
  defaultTaxRate?: number
  statusTemplate: string
  sortOrder: number
  remark?: string
  status: MasterStatus
  productCount: number
  attrDefs: AttrDef[]
  createdAt: string
}

export interface Product {
  id: string
  productNo: string
  name: string
  categoryId: string
  categoryCode: string
  categoryName: string
  trackingMode: TrackingMode
  unit: string
  brand?: string
  series?: string
  model?: string
  configCode?: string
  taxRate?: number
  minPrice?: number
  salePriceGuide?: number
  purchasePriceRef?: number
  specJson?: string
  remark?: string
  status: MasterStatus
  attrDefs: AttrDef[]
  createdAt: string
}

export interface ProductSerial {
  id: string
  serialNo: string
  productId: string
  productNo?: string
  productName?: string
  categoryCode?: string
  categoryName?: string
  status: SerialStatus
  warehouseId?: string
  warehouseName?: string
  locationCode?: string
  batchNo?: string
  purchasePrice?: number
  inboundAt?: string
  certificateApplicationDate?: string
  warrantyStart?: string
  warrantyEnd?: string
  ownershipType: OwnershipType
  attrsJson?: string
  remark?: string
  allowedNextStatuses: SerialStatus[]
  createdAt: string
}

export interface Warehouse {
  id: string
  code: string
  name: string
  type: WarehouseType
  address?: string
  ownerOrgId?: string
  status: MasterStatus
  createdAt: string
}

export interface Supplier {
  id: string
  code: string
  name: string
  supplierType: SupplierType
  contactName?: string
  contactPhone?: string
  taxNo?: string
  paymentTerms?: string
  remark?: string
  status: MasterStatus
  createdAt: string
}

export interface Customer {
  id: string
  code: string
  name: string
  customerType: CustomerType
  contactName?: string
  contactPhone?: string
  taxNo?: string
  creditLimit: number
  remark?: string
  status: MasterStatus
  createdAt: string
}

export interface PriceListLine {
  id: string
  productId: string
  productNo: string
  productName: string
  price: number
  minPrice?: number
  remark?: string
}

export interface PriceList {
  id: string
  code: string
  name: string
  customerType?: CustomerType
  effectiveFrom?: string
  effectiveTo?: string
  status: MasterStatus
  remark?: string
  lineCount: number
  lines: PriceListLine[]
  createdAt: string
}

export interface ProductPrice {
  productId: string
  productNo: string
  price?: number
  minPrice?: number
  source: string
}

export interface BatchImportResult {
  created: number
  failed: number
  errors: string[]
  items: ProductSerial[]
}

export const SERIAL_STATUS_LABELS: Record<SerialStatus, string> = {
  PLANNED: '计划',
  IN_TRANSIT: '在途',
  ARRIVED: '到店',
  IN_STOCK: '在库',
  LOCKED: '已锁定',
  SOLD: '已售',
  DELIVERED: '已交付',
  RETURNED: '退回',
  REPAIRING: '维修中',
  IN_LEASE: '租赁中',
  RETIRED: '已退役',
  SECOND_LIFE: '梯次利用',
  SCRAPPED: '已报废',
}

export const TRACKING_MODE_LABELS: Record<TrackingMode, string> = {
  QUANTITY: '数量管理',
  SERIAL: '一物一档（VIN/SN）',
}

export const WAREHOUSE_TYPE_LABELS: Record<WarehouseType, string> = {
  MAIN: '主仓',
  STORE: '门店仓',
  SECONDARY: '二级仓',
  CUSTOMER: '客户仓',
  TEMPORARY: '临时仓',
  OTHER: '其他',
}

export const SUPPLIER_TYPE_LABELS: Record<SupplierType, string> = {
  VEHICLE_OEM: '整车厂',
  BATTERY_OEM: '动力电池厂',
  FRAME_OEM: '框架厂',
  OTHER: '其他',
}

export const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = {
  INDIVIDUAL: '个人',
  COMPANY: '单位',
  FLEET: '大客户/车队',
  SECONDARY: '二级',
  OTHER: '其他',
}