import { http } from './http'
import type { OrganizationItem } from '../types/organization'

export const organizationApi = {
  async companies(): Promise<OrganizationItem[]> {
    const { data } = await http.get<OrganizationItem[]>('/platform/companies'); return data
  },
  async createCompany(code: string, name: string): Promise<OrganizationItem> {
    const { data } = await http.post<OrganizationItem>('/platform/companies', { code, name }); return data
  },
  async stores(companyId?: string): Promise<OrganizationItem[]> {
    const { data } = await http.get<OrganizationItem[]>('/platform/stores', { params: { companyId } }); return data
  },
  async createStore(companyId: string, code: string, name: string): Promise<OrganizationItem> {
    const { data } = await http.post<OrganizationItem>('/platform/stores', { companyId, code, name }); return data
  },
  async teams(storeId?: string): Promise<OrganizationItem[]> {
    const { data } = await http.get<OrganizationItem[]>('/platform/teams', { params: { storeId } }); return data
  },
  async createTeam(companyId: string, storeId: string, code: string, name: string): Promise<OrganizationItem> {
    const { data } = await http.post<OrganizationItem>('/platform/teams', { companyId, storeId, code, name }); return data
  },
}
