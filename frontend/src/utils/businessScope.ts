export type BusinessScope = 'VEHICLE' | 'BATTERY'

export function isBatteryCategory(categoryCode?: string, categoryName?: string) {
  const code = (categoryCode || '').toUpperCase()
  return code === 'BATTERY' || code.startsWith('BATTERY-') || (categoryName || '').includes('电池')
}

export function categoryScope(categoryCode?: string, categoryName?: string): BusinessScope {
  return isBatteryCategory(categoryCode, categoryName) ? 'BATTERY' : 'VEHICLE'
}

export function scopeLabel(scope?: BusinessScope) {
  return scope === 'BATTERY' ? '电池' : '整车'
}
