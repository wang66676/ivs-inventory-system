import { http } from './http'
import type { LicenseStatus } from '../types/license'

export const licenseApi = {
  status: () => http.get<LicenseStatus>('/platform/license/status').then((response) => response.data),
  importFile: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return http.post<LicenseStatus>('/platform/license/import', body).then((response) => response.data)
  },
}
