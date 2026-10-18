import { http } from './http'
import type { MigrationBatch, MigrationReconciliation, MigrationValidation } from '../types/migration'

export const migrationApi = {
  validate: (payload: Record<string, unknown>) =>
    http.post<MigrationValidation>('/ivs/migrations/validate', payload).then((r) => r.data),
  import: (payload: Record<string, unknown>) =>
    http.post<MigrationBatch>('/ivs/migrations/import', payload).then((r) => r.data),
  list: () => http.get<MigrationBatch[]>('/ivs/migrations').then((r) => r.data),
  reconcile: (id: string) =>
    http.get<MigrationReconciliation>(`/ivs/migrations/${id}/reconciliation`).then((r) => r.data),
  rollback: (id: string) =>
    http.post<MigrationBatch>(`/ivs/migrations/${id}/rollback`, {}).then((r) => r.data),
}
