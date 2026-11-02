<template>
  <div class="purchase-order-page">
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="采购明细按商品行下单：车系、型号、配置、数量、单价、返利政策和收货仓库可维护。VIN/SN 在收货时录入，收货入库独立展示并与采购明细联动。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建采购单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-button @click="downloadTemplate">模板下载</a-button>
        <a-button @click="exportOrders">导出采购单</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:170px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="订单编号" allow-clear style="width:220px" @press-enter="reload" />
      </a-space>
      <a-table v-resizable-columns :data-source="purchaseRows" :columns="columns" row-key="rowKey" size="middle"
               :pagination="false" :scroll="{ x: 1550 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orderNo'">{{ record.order.externalOrderNo || record.order.orderNo }}</template>
          <template v-else-if="column.key === 'series'">{{ record.line.series || '-' }}</template>
          <template v-else-if="column.key === 'model'">{{ modelOf(record.line.productId) }}</template>
          <template v-else-if="column.key === 'config'">{{ record.line.configText || record.line.configCode || '-' }}</template>
          <template v-else-if="column.key === 'quantity'">{{ record.line.quantity }}</template>
          <template v-else-if="column.key === 'unitPrice'">{{ money(record.line.unitPrice) }}</template>
          <template v-else-if="column.key === 'amount'"><b>{{ money(record.line.amount) }}</b></template>
          <template v-else-if="column.key === 'policies'">
            <a-space v-if="record.line.policies?.length" wrap size="small">
              <a-tag v-for="policy in record.line.policies" :key="policy.id" color="blue">{{ policy.name }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'warehouse'">{{ record.line.warehouseName || record.order.warehouseName || '收货时确认' }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="record.isFirstLine">
              <a v-if="canWrite && record.order.status === 'DRAFT'" @click="openEdit(record.order)">编辑</a>
              <a v-if="canWrite && record.order.status === 'DRAFT'" @click="approve(record.order)">审核</a>
              <a v-if="canWrite && (record.order.status === 'APPROVED' || record.order.status === 'PARTIALLY_RECEIVED')"
                 @click="openReceive(record.order)">收货</a>
              <a v-if="canWrite && (record.order.status === 'DRAFT' || record.order.status === 'APPROVED')" @click="cancel(record.order)">取消</a>
            </a-space>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="createOpen" :title="editingId ? '编辑采购单' : '新建采购单'" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="1650px" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }"
             @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="供应商" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search @change="onSupplierChange" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="预计到货"><a-date-picker v-model:value="form.expectedDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
        <a-space wrap style="margin-bottom:10px">
          <a-button @click="downloadTemplate">下载导入模板</a-button>
          <a-button :disabled="!form.supplierId" @click="triggerUpload">批量上传</a-button>
          <a-button @click="exportCurrent">导出当前明细</a-button>
          <span class="hint">CSV 字段：订单编号、车系、型号、配置、数量、单价、总计车款、返利政策、收货仓库</span>
          <input ref="fileInput" type="file" accept=".csv,text/csv" style="display:none" @change="onFileChange" />
        </a-space>
        <a-divider orientation="left" style="font-size:13px">采购明细</a-divider>
        <a-table :data-source="form.lines" row-key="_key" size="small" :pagination="false" :scroll="{ x: 1310 }">
          <a-table-column title="订单编号" :width="140"><template #default="{ record: line }"><a-input v-model:value="line.orderNo" placeholder="订单编号" /></template></a-table-column>
          <a-table-column title="车系" :width="110">
            <template #default="{ record: line }">
              <a-select v-model:value="line.productId" :options="seriesOptions" show-search placeholder="车系"
                        option-filter-prop="label" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="型号" :width="120"><template #default="{ record: line }">{{ modelOf(line.productId) }}</template></a-table-column>
          <a-table-column title="配置" :width="140"><template #default="{ record: line }"><a-input v-model:value="line.configText" placeholder="人工输入配置" /></template></a-table-column>
          <a-table-column title="数量" :width="70"><template #default="{ record: line }"><a-input-number v-model:value="line.quantity" :min="0.001" style="width:100%" /></template></a-table-column>
          <a-table-column title="单价" :width="150"><template #default="{ record: line }"><MoneyInput v-model:value="line.unitPrice" :min="0" :precision="2" style="width:100%" /></template></a-table-column>
          <a-table-column title="总计车款" :width="110" align="right"><template #default="{ record: line }"><b>{{ money(lineTotal(line)) }}</b></template></a-table-column>
          <a-table-column title="返利政策（可多选）" :width="230">
            <template #default="{ record: line }">
              <a-select v-model:value="line.policyIds" mode="multiple" :options="policyOptionsForSupplier"
                        :disabled="!form.supplierId" allow-clear show-search option-filter-prop="label"
                        :not-found-content="form.supplierId ? '该供应商暂无已发布返利政策' : '请先选择供应商'"
                        :placeholder="form.supplierId ? '可后补' : '请先选择供应商'" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="收货仓库" :width="150">
            <template #default="{ record: line }">
              <a-select v-model:value="line.warehouseId" :options="warehouseOptions" allow-clear placeholder="可后补" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="140" align="center">
            <template #default="{ record: line }">
              <a-button danger size="small" :disabled="form.lines.length <= 1" @click="removeLine(line._key)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>
        <a-button type="dashed" block style="margin-top:10px" @click="addLine">+ 增加采购明细</a-button>
      </a-form>
    </a-modal>

    <a-modal v-model:open="receiveOpen" :title="`收货 - ${current?.externalOrderNo || current?.orderNo || ''}`"
             :confirm-loading="saving" ok-text="确认收货" cancel-text="取消" width="1180px" @ok="submitReceive">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="VIN/SN 在收货时逐台录入，收货入库会按 VIN 生成独立明细并关联采购明细。仓库可按实际发运情况逐台指定。" />
      <a-form layout="vertical">
        <a-form-item label="默认收货仓库（可选）">
          <a-select v-model:value="receiveForm.warehouseId" :options="warehouseOptions" allow-clear />
        </a-form-item>
        <a-table :data-source="current?.lines ?? []" row-key="id" size="small" :pagination="false"
                 :scroll="{ x: 1000 }" :locale="{ emptyText: '无明细' }">
          <a-table-column title="车系" :width="180"><template #default="{ record }">{{ record.series || '-' }}</template></a-table-column>
          <a-table-column title="待收" data-index="remainingQuantity" :width="80" />
          <a-table-column title="本次收货" :width="620">
            <template #default="{ record }">
              <a-textarea v-if="record.trackingMode === 'SERIAL'" v-model:value="receiveInput[record.id].serialRows"
                          :rows="3" :disabled="Number(record.remainingQuantity) === 0"
                          placeholder="每行：VIN,单车采购价,收货仓库,申请合格证日期&#10;例如：LZZ1...20260001,420000,主仓,2026-10-21" />
              <a-space v-else wrap>
                <a-input-number v-model:value="receiveInput[record.id].quantity" :min="0"
                                :max="Number(record.remainingQuantity)" placeholder="本次数量" style="width:130px" />
                <a-select v-model:value="receiveInput[record.id].warehouseId" :options="warehouseOptions"
                          placeholder="收货仓库" style="width:210px" allow-clear />
              </a-space>
            </template>
          </a-table-column>
        </a-table>
        <a-form-item label="备注" style="margin-top:16px"><a-input v-model:value="receiveForm.remark" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { purchaseApi } from '../../api/procurement'
import { productApi, supplierApi, warehouseApi } from '../../api/masterdata'
import { policyApi } from '../../api/policy'
import { useAuthStore } from '../../stores/auth'
import { PURCHASE_STATUS_COLORS, PURCHASE_STATUS_LABELS } from '../../types/procurement'
import type { PurchaseOrder, PurchaseOrderLine, PurchaseOrderStatus } from '../../types/procurement'
import type { Policy } from '../../types/policy'

const statusLabel = (status: string) => PURCHASE_STATUS_LABELS[status as PurchaseOrderStatus]
const statusColor = (status: string) => PURCHASE_STATUS_COLORS[status as PurchaseOrderStatus]
const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')
const rows = ref<PurchaseOrder[]>([])
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const createOpen = ref(false)
const receiveOpen = ref(false)
const saving = ref(false)
const editingId = ref<string>()
const current = ref<PurchaseOrder | null>(null)
const fileInput = ref<HTMLInputElement>()
const supplierOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string; name: string; code: string }[]>([])
const productOptions = ref<{ label: string; value: string; productNo: string; name: string; series?: string; model?: string }[]>([])
const policies = ref<Policy[]>([])
interface PurchaseListRow {
  rowKey: string
  order: PurchaseOrder
  line: PurchaseOrderLine
  isFirstLine: boolean
}
const form = reactive<Record<string, any>>({ lines: [newLine()] })
const receiveForm = reactive<Record<string, any>>({})
const receiveInput = reactive<Record<string, { serialRows?: string; quantity?: number; warehouseId?: string }>>({})
const purchaseRows = computed<PurchaseListRow[]>(() => rows.value.flatMap((order) => order.lines.map((line, index) => ({
  rowKey: `${order.id}-${line.id}`,
  order,
  line,
  isFirstLine: index === 0,
}))))
const columns = [
  { title: '订单编号', key: 'orderNo', width: 150, ellipsis: true },
  { title: '车系', key: 'series', width: 120, ellipsis: true },
  { title: '型号', key: 'model', width: 120, ellipsis: true },
  { title: '配置', key: 'config', width: 140, ellipsis: true },
  { title: '数量', key: 'quantity', width: 80, align: 'right' },
  { title: '单价', key: 'unitPrice', width: 110, align: 'right' },
  { title: '总计车款', key: 'amount', width: 140, align: 'right' },
  { title: '返利政策', key: 'policies', width: 240 },
  { title: '收货仓库', key: 'warehouse', width: 150, ellipsis: true },
  { title: '操作', key: 'actions', width: 300, fixed: 'right' },
]
const statusOptions = Object.entries(PURCHASE_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const seriesOptions = computed(() => {
  const bySeries = new Map<string, string>()
  for (const item of productOptions.value) {
    const series = item.series || item.name
    if (series && !bySeries.has(series)) bySeries.set(series, item.value)
  }
  return [...bySeries.entries()].sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
    .map(([series, productId]) => ({ label: series, value: productId }))
})
const policyOptionsForSupplier = computed(() => policies.value
  .filter((policy) => policy.supplierId === form.supplierId)
  .map((policy) => ({ label: `${policy.policyNo} ${policy.name}`, value: policy.id })))
function newLine() {
  return { _key: `line-${Date.now()}-${Math.random()}`, orderNo: '', productId: undefined,
    configText: '', quantity: 1, unitPrice: undefined, policyIds: [] as string[], warehouseId: undefined }
}
function modelOf(productId?: string) { return productOptions.value.find((item) => item.value === productId)?.model || '-' }
function addLine() { form.lines.push(newLine()) }
function removeLine(key: string) {
  if (form.lines.length <= 1) return
  const index = (form.lines as Record<string, any>[]).findIndex((line) => line._key === key)
  if (index >= 0) form.lines.splice(index, 1)
}
function lineTotal(line: Record<string, any>) { return Number(line.quantity ?? 0) * Number(line.unitPrice ?? 0) }
function money(value?: number | null) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function seriesOf(productId?: string) { return productOptions.value.find((item) => item.value === productId)?.series || '' }
function warehouseLabel(id?: string) { return warehouseOptions.value.find((item) => item.value === id)?.name || '' }
function policyNames(ids: string[]) { return ids.map((id) => policies.value.find((policy) => policy.id === id)?.name).filter(Boolean).join('|') }
function onSupplierChange() {
  const allowed = new Set(policyOptionsForSupplier.value.map((item) => item.value))
  for (const line of form.lines as Record<string, any>[]) line.policyIds = (line.policyIds ?? []).filter((id: string) => allowed.has(id))
}
async function reload() {
  try { rows.value = await purchaseApi.list({ keyword: keyword.value || undefined, status: filterStatus.value }) }
  catch (error) { message.error((error as Error).message) }
}
function resetForm() { Object.keys(form).forEach((key) => delete form[key]); form.lines = [newLine()] }
function openCreate() { editingId.value = undefined; resetForm(); createOpen.value = true }
function openEdit(record: PurchaseOrder) {
  editingId.value = record.id; resetForm(); form.supplierId = record.supplierId
  form.expectedDate = record.expectedDate; form.remark = record.remark
  form.lines = record.lines.map((line) => ({ _key: line.id, orderNo: record.externalOrderNo || record.orderNo,
    productId: line.productId, configText: line.configText || line.configCode || '',
    quantity: line.quantity, unitPrice: line.unitPrice, policyIds: line.policies.map((policy) => policy.id),
    warehouseId: line.warehouseId }))
  createOpen.value = true
}
function resolveWarehouse(value: string) {
  const key = value.trim().toLowerCase()
  return warehouseOptions.value.find((item) => item.value.toLowerCase() === key)
    || warehouseOptions.value.find((item) => item.name.toLowerCase() === key)
    || warehouseOptions.value.find((item) => item.code.toLowerCase() === key)
}
function resolveProduct(series: string) {
  return productOptions.value.find((item) => (item.series || item.name).toLowerCase() === series.trim().toLowerCase())
}
function parseCsv(text: string): string[][] {
  const source = text.replace(/^\uFEFF/, ''); const result: string[][] = []; let row: string[] = []; let cell = ''; let quoted = false
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index]
    if (quoted) { if (char === '"' && source[index + 1] === '"') { cell += '"'; index += 1 } else if (char === '"') quoted = false; else cell += char }
    else if (char === '"') quoted = true; else if (char === ',') { row.push(cell); cell = '' }
    else if (char === '\n') { row.push(cell); result.push(row); row = []; cell = '' } else if (char !== '\r') cell += char
  }
  if (cell || row.length) { row.push(cell); result.push(row) }
  return result.filter((item) => item.some((value) => value.trim()))
}
function field(row: string[], headers: Map<string, number>, ...names: string[]) {
  for (const name of names) { const index = headers.get(name); if (index != null) return (row[index] ?? '').trim() }
  return ''
}
async function importCsv(file: File) {
  if (!form.supplierId) { message.warning('请先选择供应商，再批量上传'); return }
  try {
    const csvRows = parseCsv(await file.text()); if (csvRows.length < 2) throw new Error('CSV 中没有采购明细')
    const headers = new Map(csvRows[0].map((header, index) => [header.trim(), index])); const imported: Record<string, any>[] = []
    for (let index = 1; index < csvRows.length; index += 1) {
      const row = csvRows[index]; const orderNo = field(row, headers, '订单编号', '订单号')
      const series = field(row, headers, '车系'); const product = resolveProduct(series)
      const quantity = Number(field(row, headers, '数量'))
      const unitPrice = Number(field(row, headers, '单价')); if (!orderNo) throw new Error(`第 ${index + 1} 行缺少订单编号`)
      if (!product) throw new Error(`第 ${index + 1} 行无法匹配车系：${series}`)
      if (!Number.isFinite(quantity) || quantity <= 0) throw new Error(`第 ${index + 1} 行数量无效`)
      if (!Number.isFinite(unitPrice) || unitPrice < 0) throw new Error(`第 ${index + 1} 行单价无效`)
      const warehouseText = field(row, headers, '收货仓库'); const warehouse = warehouseText ? resolveWarehouse(warehouseText) : undefined
      if (warehouseText && !warehouse) throw new Error(`第 ${index + 1} 行仓库无法匹配：${warehouseText}`)
      const policyText = field(row, headers, '返利政策'); const policyIds: string[] = []
      if (policyText) for (const token of policyText.split(/[|；;]/).map((item) => item.trim()).filter(Boolean)) {
        const found = policyOptionsForSupplier.value.find((option) => { const policy = policies.value.find((item) => item.id === option.value)
          return policy && (policy.name === token || policy.policyNo === token || option.label === token) })
        if (!found) throw new Error(`第 ${index + 1} 行返利政策无法匹配：${token}`)
        policyIds.push(found.value)
      }
      imported.push({ _key: `line-${Date.now()}-${index}-${Math.random()}`, orderNo,
        productId: product.value, configText: field(row, headers, '配置'), quantity, unitPrice, policyIds,
        warehouseId: warehouse?.value })
    }
    form.lines = imported; message.success(`已导入 ${imported.length} 条采购明细`)
  } catch (error) { message.error((error as Error).message) }
}
function triggerUpload() { fileInput.value?.click() }
async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (file) await importCsv(file); input.value = ''
}
function csvValue(value: unknown) { return `"${String(value ?? '').replace(/"/g, '""')}"` }
function downloadCsv(filename: string, dataRows: unknown[][]) {
  const text = dataRows.map((row) => row.map(csvValue).join(',')).join('\r\n'); const blob = new Blob([`\uFEFF${text}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url)
}
function downloadTemplate() {
  downloadCsv('采购单导入模板.csv', [['订单编号', '车系', '型号', '配置', '数量', '单价', '总计车款', '返利政策', '收货仓库'],
    ['PO-2026-001', '示例车系', '6x4', '人工输入配置', 2, 420000, 840000, '政策A|政策B', '主仓']])
}
function exportCurrent() {
  const data = (form.lines as Record<string, any>[]).map((line) => [line.orderNo, seriesOf(line.productId),
    modelOf(line.productId), line.configText, line.quantity, line.unitPrice, lineTotal(line), policyNames(line.policyIds ?? []), warehouseLabel(line.warehouseId)])
  downloadCsv('采购明细.csv', [['订单编号', '车系', '型号', '配置', '数量', '单价', '总计车款', '返利政策', '收货仓库'], ...data])
}
function exportOrders() {
  const data: unknown[][] = []
  for (const order of rows.value) for (const line of order.lines) data.push([order.externalOrderNo || order.orderNo, line.series || '',
    modelOf(line.productId), line.configText || line.configCode || '', line.quantity, line.unitPrice,
    line.amount, line.policies.map((policy) => policy.name).join('|'), line.warehouseName || order.warehouseName || ''])
  downloadCsv('采购单导出.csv', [['订单编号', '车系', '型号', '配置', '数量', '单价', '总计车款', '返利政策', '收货仓库'], ...data])
}
async function submit() {
  if (!form.supplierId) { message.warning('请选择供应商'); return }
  const lines = (form.lines as Record<string, any>[]).filter((line) => line.orderNo || line.productId || line.unitPrice != null)
  if (!lines.length) { message.warning('请至少填写一行有效明细'); return }
  for (const line of lines) {
    if (!String(line.orderNo ?? '').trim()) { message.warning('每条明细都必须填写订单编号'); return }
    if (!line.productId) { message.warning('请选择车系'); return }
    if (!(Number(line.quantity) > 0)) { message.warning('数量必须大于 0'); return }
    if (line.unitPrice == null || !(Number(line.unitPrice) >= 0)) { message.warning('请填写有效单价'); return }
  }
  const grouped = new Map<string, Record<string, any>[]>()
  for (const line of lines) { const orderNo = String(line.orderNo).trim(); if (!grouped.has(orderNo)) grouped.set(orderNo, []); grouped.get(orderNo)!.push(line) }
  saving.value = true
  try {
    const bodyFor = (orderLines: Record<string, any>[]) => ({ supplierId: form.supplierId, warehouseId: null,
      expectedDate: form.expectedDate, remark: form.remark,
      lines: orderLines.map((line) => ({ productId: line.productId, quantity: line.quantity, unitPrice: line.unitPrice,
        configText: line.configText || null, warehouseId: line.warehouseId || null, policyIds: line.policyIds ?? [] })) })
    if (editingId.value) { await purchaseApi.update(editingId.value, { externalOrderNo: [...grouped.keys()][0], ...bodyFor([...grouped.values()][0]) }); message.success('采购单已更新') }
    else { for (const [orderNo, orderLines] of grouped) await purchaseApi.create({ externalOrderNo: orderNo, ...bodyFor(orderLines) }); message.success(`已创建 ${grouped.size} 张采购单`) }
    createOpen.value = false; await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
async function approve(record: PurchaseOrder) { try { await purchaseApi.approve(record.id); message.success('已审核'); await reload() } catch (error) { message.error((error as Error).message) } }
async function cancel(record: PurchaseOrder) { try { await purchaseApi.cancel(record.id); message.success('已取消'); await reload() } catch (error) { message.error((error as Error).message) } }
async function openReceive(record: PurchaseOrder) {
  try {
    const latest = await purchaseApi.get(record.id)
    const receivable = latest.status === 'APPROVED' || latest.status === 'PARTIALLY_RECEIVED'
    const hasRemaining = latest.lines.some((line) => Number(line.remainingQuantity) > 0)
    if (!receivable || !hasRemaining) {
      message.info('采购单已全部收货，无需重复收货')
      await reload()
      return
    }
    current.value = latest
    Object.keys(receiveForm).forEach((key) => delete receiveForm[key])
    Object.keys(receiveInput).forEach((key) => delete receiveInput[key])
    receiveForm.warehouseId = latest.warehouseId
    latest.lines.forEach((line) => { receiveInput[line.id] = { warehouseId: line.warehouseId, serialRows: '' } })
    receiveOpen.value = true
  } catch (error) { message.error((error as Error).message) }
}
async function submitReceive() {
  if (!current.value) return
  const lines: Record<string, unknown>[] = []; const seenVin = new Set<string>()
  for (const line of current.value.lines) {
    if (Number(line.remainingQuantity) === 0) continue
    const input = receiveInput[line.id] ?? {}
    if (line.trackingMode === 'SERIAL') {
      for (const row of String(input.serialRows ?? '').split('\n').map((item) => item.trim()).filter(Boolean)) {
        const parts = row.split(/[,，\t]/).map((item) => item.trim()); const serialNo = parts[0]?.toUpperCase(); if (!serialNo) continue
        if (seenVin.has(serialNo)) { message.warning('有重复项，请核查后再提交'); return } ; seenVin.add(serialNo)
        const unitPrice = parts[1] ? Number(parts[1]) : Number(line.unitPrice)
        if (!Number.isFinite(unitPrice) || unitPrice < 0) { message.warning(`VIN ${serialNo} 的单车采购价无效`); return }
        const warehouseText = parts[2]; const warehouse = warehouseText ? resolveWarehouse(warehouseText) : undefined
        if (warehouseText && !warehouse) { message.warning(`VIN ${serialNo} 的仓库无法匹配：${warehouseText}`); return }
        const warehouseId = warehouse?.value || line.warehouseId || receiveForm.warehouseId
        if (!warehouseId) { message.warning(`VIN ${serialNo} 请指定收货仓库`); return }
        const certDate = parts[3] || undefined
        if (certDate && (!/^\d{4}-\d{2}-\d{2}$/.test(certDate) || !dayjs(certDate).isValid())) { message.warning(`VIN ${serialNo} 的申请合格证日期格式应为 YYYY-MM-DD`); return }
        lines.push({ orderLineId: line.id, quantity: 1, serialNos: [serialNo], unitPrice, warehouseId, certificateApplicationDate: certDate || null })
      }
    } else if (input.quantity && Number(input.quantity) > 0) {
      const warehouseId = input.warehouseId || line.warehouseId || receiveForm.warehouseId
      if (!warehouseId) { message.warning(`商品 ${line.productName} 请指定收货仓库`); return }
      lines.push({ orderLineId: line.id, quantity: input.quantity, unitPrice: line.unitPrice, warehouseId })
    }
  }
  if (!lines.length) { message.warning('请填写本次收货内容'); return }
  saving.value = true
  try {
    const receipt = await purchaseApi.receive(current.value.id, { warehouseId: receiveForm.warehouseId || null, remark: receiveForm.remark, lines })
    message.success(`收货完成，金额 ${money(receipt.amount)}`); receiveOpen.value = false; await reload()
  } catch (error) {
    const text = (error as Error).message
    message.error(text)
    if (text.includes('已全部收货')) { receiveOpen.value = false; await reload() }
  } finally { saving.value = false }
}
onMounted(async () => {
  try {
    const [suppliers, warehouses, products, publishedPolicies] = await Promise.all([supplierApi.list(), warehouseApi.list(), productApi.list(), policyApi.list({ status: 'PUBLISHED' })])
    supplierOptions.value = suppliers.map((item) => ({ label: `${item.name}${item.supplierType === 'OEM' ? '（整车厂）' : ''}`, value: item.id }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id, name: item.name, code: item.code }))
    productOptions.value = products.map((item) => ({ label: item.name, value: item.id, productNo: item.productNo, name: item.name, series: item.series, model: item.model }))
    policies.value = publishedPolicies
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
:deep(.ant-table-expanded-row > td) { padding-left: 0 !important; padding-right: 0 !important; }
:deep(.ant-table-expanded-row .ant-table-wrapper) { margin-left: 0 !important; margin-right: 0 !important; }
</style>

<style>
.purchase-order-page .ant-table-expanded-row > td { padding-left: 0 !important; padding-right: 0 !important; }
.purchase-order-page .ant-table-expanded-row .ant-table-wrapper { margin-left: 0 !important; margin-right: 0 !important; }
.purchase-order-page .ant-table-expanded-row .ant-table { margin-left: 0 !important; margin-right: 0 !important; }
</style>
