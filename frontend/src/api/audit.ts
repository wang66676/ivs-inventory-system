import { http } from './http'
import type { AuditLogEntry } from '../types/audit'
export const auditApi = { async search(params?: { keyword?:string; aggregateType?:string; aggregateId?:string; limit?:number }): Promise<AuditLogEntry[]> { const { data } = await http.get<AuditLogEntry[]>('/platform/audit-logs',{params}); return data } }
