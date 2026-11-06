import { http } from './http'
import type { ApprovalRule, ApprovalRuleUpdate } from '../types/approval'

export const approvalApi = {
  list: () => http.get<ApprovalRule[]>('/ivs/approval-rules').then((r) => r.data),
  update: (ruleCode: string, body: ApprovalRuleUpdate) =>
    http.put<ApprovalRule>(`/ivs/approval-rules/${encodeURIComponent(ruleCode)}`, body).then((r) => r.data),
}
