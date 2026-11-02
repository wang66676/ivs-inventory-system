export type CsvValue = string | number | boolean | null | undefined

export interface CsvColumn<T> {
  title: string
  value: (row: T) => CsvValue
}

/** 导出带 UTF-8 BOM 的 CSV，Excel 可直接识别中文。 */
export function exportCsv<T>(fileName: string, columns: CsvColumn<T>[], rows: T[]) {
  const header = columns.map((column) => escapeCsv(column.title)).join(',')
  const body = rows.map((row) => columns.map((column) => escapeCsv(column.value(row))).join(','))
  const content = `\ufeff${[header, ...body].join('\r\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName.toLowerCase().endsWith('.csv') ? fileName : `${fileName}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function exportTimestamp() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}

/** 解析 CSV 文本，支持 UTF-8 BOM、引号字段、逗号与字段内换行。 */
export function parseCsv(text: string): string[][] {
  const content = text.replace(/^\uFEFF/, '')
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false

  for (let index = 0; index < content.length; index += 1) {
    const char = content[index]
    if (char === '"') {
      if (quoted && content[index + 1] === '"') {
        field += '"'
        index += 1
      } else {
        quoted = !quoted
      }
    } else if (char === ',' && !quoted) {
      row.push(field)
      field = ''
    } else if ((char === '\r' || char === '\n') && !quoted) {
      row.push(field)
      if (row.some((value) => value.trim() !== '')) rows.push(row)
      row = []
      field = ''
      if (char === '\r' && content[index + 1] === '\n') index += 1
    } else {
      field += char
    }
  }

  row.push(field)
  if (row.some((value) => value.trim() !== '')) rows.push(row)
  return rows
}

function escapeCsv(value: CsvValue) {
  if (value == null) return ''
  const text = String(value)
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}
