import { http } from './http'
import type { LicenseStatus } from '../types/license'

export const licenseApi = {
  status: () => http.get<LicenseStatus>('/platform/license/status').then((response) => response.data),
  activate: (activationCode: string) => http.post<LicenseStatus>('/platform/license/activate', { activationCode })
    .then((response) => response.data),
}
