export interface SystemSetting {
  key: string
  value: string
  label: string
  group: string
  type: 'STRING' | 'NUMBER' | 'PERCENT' | 'BOOLEAN'
  description?: string
  updatedAt: string
}
