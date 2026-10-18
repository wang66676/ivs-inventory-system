import type { Directive } from 'vue'

/**
 * 全局表头列宽行为：
 * - 所有 a-table 固定布局，内容不换行，超出列宽自动隐藏；
 * - 表头右侧拖拽可加宽或收窄，仅改变当前列宽，其他列宽保持不变；
 * - 列宽只记忆在当前浏览器会话（sessionStorage），退出登录时清空并恢复初始宽度。
 */

const READY_ATTR = 'data-resizable-ready'
const KEY_ATTR = 'data-resizable-key'
const SIGNATURE_ATTR = 'data-resizable-signature'
const INITIAL_WIDTHS_ATTR = 'data-initial-column-widths'
const STORAGE_PREFIX = 'ivs:table-column-widths:v3:'
const SESSION_CLEAR_EVENT = 'ivs:session-cleared'
const MIN_COLUMN_WIDTH = 60

let documentObserver: MutationObserver | null = null
let scanFrame = 0

type StoredWidths = Array<number | null>

function headerCells(el: HTMLElement): HTMLElement[] {
  return Array.from(el.querySelectorAll('.ant-table-thead > tr > th')) as HTMLElement[]
}

function headerSignature(ths: HTMLElement[]): string {
  return ths.map((th) => (th.textContent ?? '').trim()).join('|')
}

function tableKey(el: HTMLElement, ths: HTMLElement[]): string {
  const signature = headerSignature(ths)
  const tables = Array.from(document.querySelectorAll<HTMLElement>('.ant-table'))
  const sameHeaderTables = tables.filter((table) => {
    const headers = headerCells(table)
    return headers.length === ths.length && headerSignature(headers) === signature
  })
  const occurrence = Math.max(0, sameHeaderTables.indexOf(el))
  return `${window.location.pathname}::${signature}::${occurrence}`
}

function readStoredWidths(key: string): StoredWidths {
  try {
    const raw = sessionStorage.getItem(STORAGE_PREFIX + key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map((value) => (Number.isFinite(Number(value)) ? Number(value) : null))
  } catch {
    return []
  }
}

function writeStoredWidths(key: string, widths: StoredWidths) {
  try {
    sessionStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(widths))
  } catch {
    // 浏览器禁用会话存储时，只保留当前 DOM 内的拖拽结果。
  }
}

function getInitialWidths(el: HTMLElement, ths: HTMLElement[], cols: HTMLElement[]): number[] {
  const cached = el.getAttribute(INITIAL_WIDTHS_ATTR)
  if (cached) {
    try {
      const parsed = JSON.parse(cached)
      if (Array.isArray(parsed) && parsed.length >= ths.length) {
        return parsed.slice(0, ths.length).map((value, index) => {
          const width = Number(value)
          return width > 0 ? Math.round(width) : Math.round(ths[index].getBoundingClientRect().width)
        })
      }
    } catch {
      // 缓存损坏时按当前布局重新获取初始宽度。
    }
  }

  const widths = ths.map((th, index) => {
    const declared = Number.parseFloat(cols[index]?.style.width || '')
    const current = th.getBoundingClientRect().width
    return Math.max(MIN_COLUMN_WIDTH, Math.round(Number.isFinite(declared) && declared > 0 ? declared : current))
  })
  el.setAttribute(INITIAL_WIDTHS_ATTR, JSON.stringify(widths))
  return widths
}

function applyColumnWidth(th: HTMLElement, width: number) {
  const next = Math.max(MIN_COLUMN_WIDTH, Math.round(width))
  th.style.setProperty('width', `${next}px`, 'important')
  th.style.setProperty('min-width', `${next}px`, 'important')
  return next
}

function applyAllColumnWidths(ths: HTMLElement[], table: HTMLElement | null, widths: number[]) {
  const applied = ths.map((th, index) => applyColumnWidth(th, widths[index]))
  if (table) {
    const total = applied.reduce((sum, width) => sum + width, 0)
    table.style.setProperty('--ivs-table-width', `${total}px`)
  }
  return applied
}

function clearStoredColumnWidths() {
  try {
    const keys: string[] = []
    for (let index = 0; index < sessionStorage.length; index += 1) {
      const key = sessionStorage.key(index)
      if (key?.startsWith(STORAGE_PREFIX)) keys.push(key)
    }
    keys.forEach((key) => sessionStorage.removeItem(key))
  } catch {
    // ignore
  }
}

function resetTableWidths(el: HTMLElement) {
  const ths = headerCells(el)
  const cols = Array.from(el.querySelectorAll('.ant-table colgroup > col')) as HTMLElement[]
  const table = el.querySelector('table') as HTMLElement | null
  if (ths.length) {
    applyAllColumnWidths(ths, table, getInitialWidths(el, ths, cols))
  } else {
    ths.forEach((th) => {
      th.style.removeProperty('width')
      th.style.removeProperty('min-width')
    })
    table?.style.removeProperty('--ivs-table-width')
  }
  delete el.dataset.resizableKey
  delete el.dataset.resizableSignature
}

export function resetAllTableColumnWidths() {
  document.querySelectorAll<HTMLElement>('.ant-table').forEach(resetTableWidths)
  clearStoredColumnWidths()
}

function addResizeHandle(ths: HTMLElement[], th: HTMLElement, index: number, table: HTMLElement | null,
                         key: string) {
  th.setAttribute(READY_ATTR, '1')
  if (getComputedStyle(th).position === 'static') th.style.position = 'relative'

  const handle = document.createElement('span')
  handle.className = 'column-resize-handle'
  handle.tabIndex = 0
  handle.setAttribute('role', 'separator')
  handle.setAttribute('aria-orientation', 'vertical')
  handle.setAttribute('aria-label', `${(th.textContent ?? '').trim()}列宽调整`)
  handle.addEventListener('mouseenter', () => {
    handle.style.background =
      'linear-gradient(to right, transparent 2px, rgba(22,119,255,.55) 2px, rgba(22,119,255,.55) 5px, transparent 5px)'
  })
  handle.addEventListener('mouseleave', () => {
    handle.style.background = 'transparent'
  })

  handle.addEventListener('keydown', (event: KeyboardEvent) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    event.stopPropagation()
    const widths = ths.map((header) => Math.max(MIN_COLUMN_WIDTH, Math.round(header.getBoundingClientRect().width)))
    const delta = event.key === 'ArrowRight' ? 10 : -10
    widths[index] = Math.max(MIN_COLUMN_WIDTH, widths[index] + delta)
    const applied = applyAllColumnWidths(ths, table, widths)
    writeStoredWidths(key, applied)
  })

  handle.addEventListener('mousedown', (event: MouseEvent) => {
    event.preventDefault()
    event.stopPropagation()

    const startX = event.clientX
    if (table) table.style.tableLayout = 'fixed'

    // 拖拽开始时冻结所有列当前宽度；之后只改变当前列，表格总宽度同步增减。
    const widths = ths.map((header) => Math.max(MIN_COLUMN_WIDTH, Math.round(header.getBoundingClientRect().width)))
    const applied = applyAllColumnWidths(ths, table, widths)
    const startWidth = applied[index]
    writeStoredWidths(key, applied)

    const onMove = (moveEvent: MouseEvent) => {

      const next = Math.max(MIN_COLUMN_WIDTH, Math.round(startWidth + (moveEvent.clientX - startX)))
      widths[index] = next
      const nextWidths = applyAllColumnWidths(ths, table, widths)
      writeStoredWidths(key, nextWidths)
    }

    const onUp = () => {

      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
      handle.style.background = 'transparent'
    }

    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
  })

  th.appendChild(handle)
}

function enhance(el: HTMLElement) {
  const ths = headerCells(el)
  if (ths.length === 0) return

  const signature = headerSignature(ths)
  const previousSignature = el.getAttribute(SIGNATURE_ATTR)
  if (previousSignature && previousSignature !== signature) {
    el.querySelectorAll('.column-resize-handle').forEach((handle) => handle.remove())
    ths.forEach((th) => th.removeAttribute(READY_ATTR))
    delete el.dataset.resizableKey
    el.removeAttribute(INITIAL_WIDTHS_ATTR)
  }
  el.setAttribute(SIGNATURE_ATTR, signature)

  if (!el.getAttribute(KEY_ATTR)) {
    el.setAttribute(KEY_ATTR, tableKey(el, ths))
  }

  const table = el.querySelector('table') as HTMLElement | null
  if (table) table.style.tableLayout = 'fixed'
  const cols = Array.from(el.querySelectorAll('.ant-table colgroup > col')) as HTMLElement[]
  const key = el.getAttribute(KEY_ATTR) ?? tableKey(el, ths)
  const initial = getInitialWidths(el, ths, cols)
  const stored = readStoredWidths(key)
  const widths = stored.length >= ths.length
    ? ths.map((th, index) => {
        const width = Number(stored[index])
        return width > 0 ? width : initial[index]
      })
    : initial

  applyAllColumnWidths(ths, table, widths)
  ths.forEach((th, index) => {
    if (th.getAttribute(READY_ATTR) !== '1') {
      addResizeHandle(ths, th, index, table, key)
    }
  })
}

export function installGlobalTableColumnBehavior() {
  if (documentObserver) return

  const scan = () => {
    if (scanFrame) return
    scanFrame = window.requestAnimationFrame(() => {
      scanFrame = 0
      document.querySelectorAll<HTMLElement>('.ant-table').forEach(enhance)
    })
  }

  scan()
  documentObserver = new MutationObserver(scan)
  documentObserver.observe(document.body, { childList: true, subtree: true })
  window.addEventListener(SESSION_CLEAR_EVENT, resetAllTableColumnWidths)
}

export const resizableColumns: Directive<HTMLElement> = {
  mounted(el) {
    enhance(el)
    const observer = new MutationObserver(() => enhance(el))
    observer.observe(el, { childList: true, subtree: true })
    ;(el as unknown as Record<string, unknown>).__columnResizeObserver = observer
  },
  unmounted(el) {
    const observer = (el as unknown as Record<string, unknown>).__columnResizeObserver as
      | MutationObserver
      | undefined
    observer?.disconnect()
  },
}




