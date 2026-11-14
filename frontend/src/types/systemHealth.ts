export type SystemHealthStatus = 'OK' | 'WARN' | 'ERROR'

export interface SystemHealthCheck {
  code: string
  label: string
  status: SystemHealthStatus
  detail: string
}

export interface SystemHealthView {
  status: SystemHealthStatus
  version: string
  checkedAt: string
  checks: SystemHealthCheck[]
}

export interface BackupResult {
  fileName: string
  path: string
  sizeBytes: number
  restoreVerified: boolean
  attachmentFiles: number
  createdAt: string
}