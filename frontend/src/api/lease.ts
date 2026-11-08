import { http } from './http'
import type {
  LeaseAssetReport, LeaseAutomationResult, LeaseContract, LeaseDepositCommand, LeaseDueGenerationResult,
  LeaseEndCommand, LeaseHealthCommand, LeaseHealthRecord,
  LeaseRenewCommand,
} from '../types/lease'

export const leaseApi = {
  list: () => http.get<LeaseContract[]>('/ivs/lease-contracts').then((r) => r.data),
  get: (id: string) => http.get<LeaseContract>(`/ivs/lease-contracts/${id}`).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<LeaseContract>('/ivs/lease-contracts', body).then((r) => r.data),
  generateDue: (id: string, throughDate?: string) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/generate-due`, {}, { params: { throughDate } }).then((r) => r.data),
  generateDueBatch: (throughDate?: string) =>
    http.post<LeaseDueGenerationResult>('/ivs/lease-contracts/generate-due-batch', {}, { params: { throughDate } }).then((r) => r.data),
  runAutomation: (asOfDate?: string, reminderDays?: number) =>
    http.post<LeaseAutomationResult>('/ivs/lease-contracts/run-automation', {}, {
      params: { asOfDate, reminderDays },
    }).then((r) => r.data),
  end: (id: string, body: LeaseEndCommand) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/end`, body).then((r) => r.data),
  collectDeposit: (id: string, body: LeaseDepositCommand) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/deposits/collect`, body).then((r) => r.data),
  refundDeposit: (id: string, body: LeaseDepositCommand) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/deposits/refund`, body).then((r) => r.data),
  renew: (id: string, body: LeaseRenewCommand) =>
    http.post<LeaseContract>(`/ivs/lease-contracts/${id}/renew`, body).then((r) => r.data),
  healthRecords: (id: string) =>
    http.get<LeaseHealthRecord[]>(`/ivs/lease-contracts/${id}/health-records`).then((r) => r.data),
  createHealthRecord: (id: string, body: LeaseHealthCommand) =>
    http.post<LeaseHealthRecord>(`/ivs/lease-contracts/${id}/health-records`, body).then((r) => r.data),
  assetReport: () => http.get<LeaseAssetReport>('/ivs/lease-assets/report').then((r) => r.data),
}
