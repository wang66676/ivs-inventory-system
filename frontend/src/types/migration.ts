export interface MigrationValidation {
  valid: boolean
  errors: string[]
  counts: Record<string, number>
  totalAmount: number
}

export interface MigrationBatch {
  id: string
  batchNo: string
  name: string
  sourceSystem?: string
  status: 'IMPORTED' | 'ROLLED_BACK'
  itemCount: number
  totalAmount: number
  remark?: string
  createdBy?: string
  createdAt: string
  importedAt?: string
  rolledBackAt?: string
}

export interface MigrationReconciliation {
  batchId: string
  batchNo: string
  ok: boolean
  itemCount: number
  missingTargets: string[]
  messages: string[]
}
