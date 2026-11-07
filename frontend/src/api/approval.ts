import { http } from './http'
import type { ApprovalRule, ApprovalRuleUpdate, ApprovalTask, NotificationItem } from '../types/approval'

export const approvalApi = {
  list: () => http.get<ApprovalRule[]>('/ivs/approval-rules').then((r) => r.data),
  update: (ruleCode: string, body: ApprovalRuleUpdate) =>
    http.put<ApprovalRule>(`/ivs/approval-rules/${encodeURIComponent(ruleCode)}`, body).then((r) => r.data),
  tasks: (status?: string) => http.get<ApprovalTask[]>('/ivs/approval-tasks', { params: { status } }).then((r) => r.data),
  approveTask: (id: string) => http.post<ApprovalTask>(`/ivs/approval-actions/${id}/approve`).then((r) => r.data),
  rejectTask: (id: string, reason: string) =>
    http.post<ApprovalTask>(`/ivs/approval-actions/${id}/reject`, { reason }).then((r) => r.data),
}

export const notificationApi = {
  list: (unreadOnly = false) => http.get<NotificationItem[]>('/ivs/notifications', { params: { unreadOnly } }).then((r) => r.data),
  unreadCount: () => http.get<{ count: number }>('/ivs/notifications/unread-count').then((r) => r.data.count),
  markRead: (id: string) => http.post<NotificationItem>(`/ivs/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => http.post<{ count: number }>('/ivs/notifications/read-all').then((r) => r.data.count),
}
