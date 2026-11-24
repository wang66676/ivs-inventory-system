export type LicenseState = 'TRIAL' | 'LICENSED' | 'EXPIRED'

export interface LicenseStatus {
  installationId: string
  state: LicenseState
  daysRemaining: number
  trialStartedAt: string
  trialEndsAt: string
  licensedUntil?: string
  customerName?: string
  contactEmail: string
  message: string
}
