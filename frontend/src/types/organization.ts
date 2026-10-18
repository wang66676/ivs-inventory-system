export type OrganizationStatus = 'ACTIVE' | 'DISABLED'
export interface OrganizationItem {
  id: string
  companyId?: string
  storeId?: string
  code: string
  name: string
  status: OrganizationStatus
  createdAt: string
}
