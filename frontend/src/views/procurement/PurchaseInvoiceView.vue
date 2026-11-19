<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="上传 PDF 后自动识别销售方厂家、发票类型、发票号码、VIN/SN 和价税合计金额；登记后可继续匹配应付。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">单张登记蓝字</a-button>
        <a-button v-if="canWrite" @click="openBatch">批量上传蓝字</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="supplierId" :options="supplierOptions" allow-clear show-search
                  placeholder="按厂家筛选" style="width:240px" @change="reload" />
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1780 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'RED' ? 'red' : 'blue'">{{ record.type === 'RED' ? '进项红字' : '进项蓝字' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'supplier'">{{ supplierName(record.supplierId) }}</template>
          <template v-else-if="column.key === 'purchaseOrder'">{{ orderNoById[record.purchaseOrderId || ''] || '-' }}</template>
          <template v-else-if="column.key === 'serialNos'">
            <a-space v-if="record.serialNos?.length" wrap size="small">
              <a-tag v-for="serialNo in record.serialNos" :key="serialNo">{{ serialNo }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'tax'">{{ money(record.taxAmount) }}</template>
          <template v-else-if="column.key === 'matched'">{{ money(record.matchedAmount) }}</template>
          <template v-else-if="column.key === 'unmatched'">{{ money(record.unmatchedAmount) }}</template>
          <template v-else-if="column.key === 'issuedAt'">{{ formatDate(record.issuedAt) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDate(record.createdAt) }}</template>
          <template v-else-if="column.key === 'pdf'">
            <a-button v-if="record.pdfFileName" type="link" size="small" style="padding:0" @click="downloadPdf(record)">
              {{ record.pdfFileName }}
            </a-button>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space size="small">
              <a-button v-if="canWrite && record.type === 'BLUE' && Number(record.unmatchedAmount || 0) > 0"
                        size="small" type="primary" @click="autoMatch(record)">匹配应付</a-button>
              <span v-if="!record.pdfFileName && !(canWrite && record.type === 'BLUE' && Number(record.unmatchedAmount || 0) > 0)">-</span>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="登记进项蓝字发票" :confirm-loading="saving" ok-text="登记蓝字"
             cancel-text="取消" width="760px" @ok="save">
      <a-form layout="vertical">
        <a-upload :before-upload="extractPdf" :show-upload-list="false" accept=".pdf,application/pdf"
                  :disabled="extracting">
          <a-button :loading="extracting">上传电子发票 PDF 并自动识别</a-button>
        </a-upload>
        <a-alert v-if="pdfExtraction" class="pdf-alert" :type="pdfExtraction.textExtracted ? 'info' : 'warning'"
                 show-icon :message="pdfExtractionMessage" />
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="销售方（厂家）" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search
                        placeholder="上传 PDF 自动匹配销售方" @change="onSupplierChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="采购订单编号" required>
              <a-select v-model:value="form.purchaseOrderId" :options="orderOptions" show-search allow-clear
                        :disabled="!form.supplierId" placeholder="仅显示未开票订单" @change="onOrderChange" />
              <div v-if="linkedOrderNoSummary" class="linked-summary">{{ linkedOrderNoSummary }}</div>
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="VIN / SN（车辆识别代号/车架号码，可多选）" required>
              <a-select v-model:value="form.serialNos" mode="multiple" :options="serialOptions"
                        :disabled="!form.purchaseOrderId && !pdfExtraction?.vinCandidates?.length" placeholder="上传 PDF 自动识别并匹配已收货 VIN"
                        @change="onSerialChange" />
              <div v-if="form.serialNos?.length" class="linked-summary">{{ form.serialNos.join('、') }}</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票类型">
              <a-input v-model:value="form.invoiceType" placeholder="上传 PDF 后读取“电子发票（…）”" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票号码">
              <a-input v-model:value="form.externalInvoiceNo" placeholder="与发票票面号码保持一致" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票金额（价税合计）" required>
              <MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" disabled style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="税率"><a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="开票日期"><a-date-picker v-model:value="form.issuedAt" format="YYYY-MM-DD" placeholder="选择年月日" style="width:100%" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="batchOpen" title="批量上传并登记进项蓝字发票" :footer="null" width="1180px">
      <a-alert type="info" show-icon class="pdf-alert"
               message="可一次选择多张 PDF。系统会逐张识别厂家、采购订单、VIN/SN、发票号码和金额，识别成功的发票可一键批量登记；失败项会保留原因，不影响其他发票。" />
      <input ref="batchInput" type="file" accept=".pdf,application/pdf" multiple class="batch-file-input"
             @change="onBatchFilesSelected" />
      <a-space wrap class="batch-actions">
        <a-button type="primary" :loading="batchPreparing" @click="batchInput?.click()">选择多张 PDF</a-button>
        <a-button :loading="batchSaving" :disabled="!batchReadyCount || batchPreparing" @click="saveBatch">
          批量登记（{{ batchReadyCount }}）
        </a-button>
        <a-button :disabled="batchPreparing || batchSaving" @click="resetBatch">清空列表</a-button>
        <span class="batch-summary">共 {{ batchRows.length }} 张，成功 {{ batchSuccessCount }} 张，失败 {{ batchFailedCount }} 张</span>
      </a-space>
      <a-table :data-source="batchRows" :columns="batchColumns" row-key="uid" size="small"
               :pagination="false" :scroll="{ x: 1050, y: 420 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileName'">{{ record.fileName }}</template>
          <template v-else-if="column.key === 'invoiceNo'">{{ record.invoiceNo || '-' }}</template>
          <template v-else-if="column.key === 'supplier'">{{ record.supplierName || '-' }}</template>
          <template v-else-if="column.key === 'orderNo'">{{ record.orderNo || '-' }}</template>
          <template v-else-if="column.key === 'serialNos'">{{ record.serialNos?.join('、') || '-' }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="batchStatusColor(record.status)">{{ batchStatusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'message'">
            <span :class="{ 'batch-error': record.status === 'FAILED' }">{{ record.message || '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { purchaseInvoiceApi } from '../../api/billing'
import { purchaseApi } from '../../api/procurement'
import { payableApi } from '../../api/payable'
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { PurchaseInvoicePdfExtraction, PurchaseRedInvoice } from '../../types/billing'
import type { Supplier } from '../../types/masterdata'
import type { Payable } from '../../types/payable'
import type { PurchaseOrder, PurchaseReceipt } from '../../types/procurement'

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_WRITE')
const rows = ref<PurchaseRedInvoice[]>([])
const allInvoices = ref<PurchaseRedInvoice[]>([])
const payables = ref<Payable[]>([])
const receipts = ref<PurchaseReceipt[]>([])
const purchaseOrders = ref<PurchaseOrder[]>([])
const supplierId = ref<string>()
const supplierOptions = ref<{ label: string; value: string }[]>([])
const supplierNames = ref<Record<string, string>>({})
const suppliers = ref<Supplier[]>([])
const extracting = ref(false)
const pdfExtraction = ref<PurchaseInvoicePdfExtraction>()
const formOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, any>>({})
type PurchaseBatchStatus = 'PENDING' | 'PARSING' | 'READY' | 'SAVING' | 'SUCCESS' | 'FAILED'
interface PurchaseBatchRow {
  uid: string
  fileName: string
  file: File
  status: PurchaseBatchStatus
  invoiceNo?: string
  supplierName?: string
  orderNo?: string
  serialNos?: string[]
  amount?: number
  message?: string
  command?: Record<string, unknown>
}
const batchOpen = ref(false)
const batchInput = ref<HTMLInputElement>()
const batchPreparing = ref(false)
const batchSaving = ref(false)
const batchRows = ref<PurchaseBatchRow[]>([])
const columns = [
  { title: '内部发票号', key: 'invoiceNo', dataIndex: 'invoiceNo', width: 150 },
  { title: '业务类型', key: 'type', width: 110 },
  { title: '发票类型', key: 'invoiceType', dataIndex: 'invoiceType', width: 220 },
  { title: '厂家', key: 'supplier', width: 190 },
  { title: '采购订单编号', key: 'purchaseOrder', width: 170 },
  { title: 'VIN / SN', key: 'serialNos', width: 260 },
  { title: '价税合计', key: 'amount', width: 140, align: 'right' },
  { title: '税额', key: 'tax', width: 130, align: 'right' },
  { title: '已匹配应付', key: 'matched', width: 140, align: 'right' },
  { title: '未匹配', key: 'unmatched', width: 140, align: 'right' },
  { title: '发票号码', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 180 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '开票日期', key: 'issuedAt', width: 130 },
  { title: '电子发票', key: 'pdf', width: 180 },
  { title: '登记日期', key: 'createdAt', width: 120 },
  { title: '操作', key: 'actions', width: 120, fixed: 'right' },
]

const batchColumns = [
  { title: 'PDF 文件', key: 'fileName', width: 190 },
  { title: '发票号码', key: 'invoiceNo', width: 170 },
  { title: '厂家', key: 'supplier', width: 170 },
  { title: '采购订单编号', key: 'orderNo', width: 160 },
  { title: 'VIN/SN', key: 'serialNos', width: 280 },
  { title: '金额', key: 'amount', width: 120, align: 'right' },
  { title: '状态', key: 'status', width: 100, align: 'center' },
  { title: '处理说明', key: 'message', width: 280 },
]
const orderNoById = computed(() => Object.fromEntries([
  ...payables.value.map((item) => [item.purchaseOrderId || '', item.orderNo || ''] as const),
  ...purchaseOrders.value.map((item) => [item.id, item.externalOrderNo || item.orderNo] as const),
]))
const orderOptions = computed(() => {
  const supplierOrders = payables.value.filter((item) =>
    item.supplierId === form.supplierId && Number(item.uninvoicedAmount || 0) > 0 && item.purchaseOrderId)
  const amounts = new Map<string, number>()
  for (const item of supplierOrders) {
    const orderId = item.purchaseOrderId!
    amounts.set(orderId, (amounts.get(orderId) || 0) + Number(item.uninvoicedAmount || 0))
  }
  return [...amounts.entries()]
    .filter(([orderId]) => receipts.value.some((receipt) =>
      receipt.orderId === orderId && receipt.status === 'POSTED' &&
      receipt.lines.some((line) => line.serialNos?.length)))
    .map(([orderId, amount]) => ({
      label: `${orderNoById.value[orderId] || orderId}（未开票 ${money(amount)}）`,
      value: orderId,
    }))
})
const serialOptions = computed(() => {
  const invoiced = new Set(form.purchaseOrderId ? allInvoices.value
    .filter((row) => row.type === 'BLUE' && row.purchaseOrderId === form.purchaseOrderId)
    .flatMap((row) => row.serialNos || []) : [])
  const options: { label: string; value: string; amount: number }[] = []
  if (form.purchaseOrderId) {
    for (const receipt of receipts.value) {
      if (receipt.orderId !== form.purchaseOrderId || receipt.status !== 'POSTED') continue
      for (const line of receipt.lines) {
        for (const serialNo of line.serialNos || []) {
          if (invoiced.has(serialNo)) continue
          options.push({
            label: `${serialNo}（${money(line.unitPrice)}）`,
            value: serialNo,
            amount: Number(line.unitPrice || 0),
          })
        }
      }
    }
  }
  for (const vin of pdfExtraction.value?.vinCandidates || []) {
    if (!options.some((item) => item.value === vin)) {
      options.push({ label: vin, value: vin, amount: 0 })
    }
  }
  return options
})
function serialValues(value?: string) {
  return String(value || '').split(/[、,，\s]+/).map((item) => item.trim()).filter(Boolean)
}

function orderIdsForVins(vins: string[]) {
  const vinSet = new Set(vins)
  const orderIds = new Set<string>()
  for (const order of purchaseOrders.value) {
    if (order.lines.some((line) => serialValues(line.serialNo).some((serialNo) => vinSet.has(serialNo)))) {
      orderIds.add(order.id)
    }
  }
  for (const receipt of receipts.value) {
    if (receipt.status !== 'POSTED') continue
    if (receipt.lines.some((line) => (line.serialNos || []).some((serialNo) => vinSet.has(serialNo)))) {
      orderIds.add(receipt.orderId)
    }
  }
  return orderIds
}

const linkedOrderNoSummary = computed(() => {
  const orderIds = orderIdsForVins(form.serialNos || [])
  const linkedOrderNos = [...new Set([...orderIds]
    .map((orderId) => orderNoById.value[orderId])
    .filter(Boolean))]
  if (linkedOrderNos.length) return linkedOrderNos.join('、')
  const extractedOrderNos = pdfExtraction.value?.orderNoCandidates || []
  if (extractedOrderNos.length) return extractedOrderNos.join('、')
  return form.purchaseOrderId ? orderNoById.value[form.purchaseOrderId] || '' : ''
})
const pdfExtractionMessage = computed(() => {
  const seller = pdfExtraction.value?.sellerName ? `识别销售方：${pdfExtraction.value.sellerName}` : ''
  return [seller, pdfExtraction.value?.warning].filter(Boolean).join('；')
})

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function formatDateTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}
function supplierName(id: string) { return supplierNames.value[id] || id }
function resetOrderSelection() {
  form.purchaseOrderId = undefined
  form.serialNos = []
  form.amount = 0
}
function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { amount: 0, taxRate: 0.13, serialNos: [], issuedAt: dayjs(), invoiceType: '' })
  pdfExtraction.value = undefined
  formOpen.value = true
}
function onSupplierChange() {
  resetOrderSelection()
}
function onOrderChange() {
  form.serialNos = []
  form.amount = pdfExtraction.value?.amount != null ? Number(pdfExtraction.value.amount) : 0
}
function onSerialChange(serialNos: string[]) {
  if (pdfExtraction.value?.amount != null) {
    form.amount = Number(pdfExtraction.value.amount)
    return
  }
  const selected = new Set(serialNos || [])
  form.amount = Math.round(serialOptions.value
    .filter((item) => selected.has(item.value))
    .reduce((sum, item) => sum + item.amount, 0) * 100) / 100
}
const batchReadyCount = computed(() => batchRows.value.filter((row) => row.status === 'READY').length)
const batchSuccessCount = computed(() => batchRows.value.filter((row) => row.status === 'SUCCESS').length)
const batchFailedCount = computed(() => batchRows.value.filter((row) => row.status === 'FAILED').length)

function batchStatusLabel(status: PurchaseBatchStatus) {
  return { PENDING: '待识别', PARSING: '识别中', READY: '可登记', SAVING: '登记中', SUCCESS: '已登记', FAILED: '失败' }[status]
}
function batchStatusColor(status: PurchaseBatchStatus) {
  return { PENDING: 'default', PARSING: 'processing', READY: 'blue', SAVING: 'processing', SUCCESS: 'green', FAILED: 'red' }[status]
}
function openBatch() {
  batchOpen.value = true
}
function resetBatch() {
  batchRows.value = []
}
function ensureBatchInvoiceUnique(invoiceNo: string, currentUid: string) {
  const normalized = invoiceNo.trim().toUpperCase()
  if (allInvoices.value.some((row) => row.type === 'BLUE' &&
    String(row.externalInvoiceNo || '').trim().toUpperCase() === normalized)) {
    throw new Error(`发票号码 ${invoiceNo} 已存在，不能重复登记`)
  }
  if (batchRows.value.some((row) => row.uid !== currentUid && row.status !== 'FAILED' && row.invoiceNo &&
    row.invoiceNo.trim().toUpperCase() === normalized)) {
    throw new Error(`本次上传中发票号码 ${invoiceNo} 重复`)
  }
}
function preparePurchaseBatchRow(file: File, extraction: PurchaseInvoicePdfExtraction, currentUid: string) {
  if (extraction.type === 'RED') throw new Error('识别为红字发票，不能走蓝字批量登记')
  const invoiceNo = extraction.invoiceNo?.trim()
  if (!invoiceNo) throw new Error('未识别到发票号码')
  ensureBatchInvoiceUnique(invoiceNo, currentUid)
  const vinCandidates = [...new Set((extraction.vinCandidates || []).map((vin) => vin.trim().toUpperCase()).filter(Boolean))]
  if (!vinCandidates.length) throw new Error('未识别到 VIN/SN')
  const orderIds = orderIdsForVins(vinCandidates)
  const orderCandidates = purchaseOrders.value.filter((order) =>
    extraction.orderNoCandidates.some((candidate) => candidate === order.externalOrderNo || candidate === order.orderNo)
    || orderIds.has(order.id))
  if (orderCandidates.length !== 1) {
    throw new Error(orderCandidates.length > 1 ? '识别结果对应多个采购订单，请改用单张登记核对' : '未匹配到采购订单')
  }
  const order = orderCandidates[0]
  const matchedSupplier = suppliers.value.find((item) =>
    (extraction.sellerTaxNo && item.taxNo && item.taxNo.toUpperCase() === extraction.sellerTaxNo.toUpperCase())
    || (extraction.sellerName && item.name && (item.name.includes(extraction.sellerName) || extraction.sellerName.includes(item.name))))
  if (matchedSupplier && matchedSupplier.id !== order.supplierId) {
    throw new Error('发票销售方与采购订单厂家不一致')
  }
  const orderSerialMap = new Map<string, string>()
  for (const line of order.lines) {
    for (const serialNo of serialValues(line.serialNo)) orderSerialMap.set(serialNo.toUpperCase(), serialNo)
  }
  for (const receipt of receipts.value.filter((item) => item.orderId === order.id && item.status === 'POSTED')) {
    for (const line of receipt.lines) {
      for (const serialNo of line.serialNos || []) orderSerialMap.set(serialNo.toUpperCase(), serialNo)
    }
  }
  const invalidVins = vinCandidates.filter((vin) => !orderSerialMap.has(vin))
  if (invalidVins.length) throw new Error(`以下 VIN/SN 不属于该采购订单：${invalidVins.join('、')}`)
  const serialNos = vinCandidates.map((vin) => orderSerialMap.get(vin)!)
  const alreadyInvoiced = new Set(allInvoices.value
    .filter((row) => row.type === 'BLUE' && row.purchaseOrderId === order.id)
    .flatMap((row) => row.serialNos || [])
    .map((serialNo) => serialNo.toUpperCase()))
  const duplicatedVins = serialNos.filter((serialNo) => alreadyInvoiced.has(serialNo.toUpperCase()))
  if (duplicatedVins.length) throw new Error(`以下 VIN/SN 已登记进项蓝字发票：${duplicatedVins.join('、')}`)
  const batchVins = new Set(batchRows.value
    .filter((row) => row.status !== 'FAILED')
    .flatMap((row) => row.serialNos || [])
    .map((serialNo) => serialNo.toUpperCase()))
  const batchDuplicateVins = serialNos.filter((serialNo) => batchVins.has(serialNo.toUpperCase()))
  if (batchDuplicateVins.length) throw new Error(`本次上传中 VIN/SN 重复：${batchDuplicateVins.join('、')}`)
  if (extraction.amount == null || Number(extraction.amount) <= 0) throw new Error('未识别到有效的价税合计金额')
  if (!extraction.issuedAt) throw new Error('未识别到开票日期')
  return {
    supplierName: order.supplierName || supplierNames.value[order.supplierId] || order.supplierId,
    orderNo: order.externalOrderNo || order.orderNo,
    serialNos,
    amount: Number(extraction.amount),
    command: {
      supplierId: order.supplierId,
      purchaseOrderId: order.id,
      serialNos,
      amount: Number(extraction.amount),
      taxRate: extraction.taxRate ?? 0.13,
      invoiceType: extraction.invoiceType || null,
      externalInvoiceNo: invoiceNo,
      issuedAt: dayjs(extraction.issuedAt).startOf('day').toDate().toISOString(),
      pdfFileName: extraction.fileName,
      pdfStorageKey: extraction.storageKey,
      extractedText: extraction.rawText || null,
      remark: '批量PDF上传',
    },
  }
}
async function onBatchFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  const pdfFiles = files.filter((file) => file.name.toLowerCase().endsWith('.pdf'))
  if (pdfFiles.length !== files.length) message.warning(`已忽略 ${files.length - pdfFiles.length} 个非 PDF 文件`)
  batchPreparing.value = true
  try {
    for (const file of pdfFiles) {
      const row = reactive<PurchaseBatchRow>({
        uid: typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
        fileName: file.name,
        file,
        status: 'PARSING',
        message: '正在识别 PDF…',
      })
      batchRows.value.push(row)
      let extraction: PurchaseInvoicePdfExtraction | undefined
      try {
        extraction = await purchaseInvoiceApi.extractPdf(file)
        row.invoiceNo = extraction.invoiceNo
        row.amount = extraction.amount == null ? undefined : Number(extraction.amount)
        const prepared = preparePurchaseBatchRow(file, extraction, row.uid)
        row.supplierName = prepared.supplierName
        row.orderNo = prepared.orderNo
        row.serialNos = prepared.serialNos
        row.amount = prepared.amount
        row.command = prepared.command
        row.status = 'READY'
        row.message = extraction.warning || '识别成功，可批量登记'
      } catch (error) {
        row.status = 'FAILED'
        const detail = extraction?.warning ? `；识别提示：${extraction.warning}` : ''
        row.message = `${(error as Error).message}${detail}`
      }
    }
  } finally {
    batchPreparing.value = false
  }
}
async function saveBatch() {
  const readyRows = batchRows.value.filter((row) => row.status === 'READY')
  if (!readyRows.length) { message.warning('没有可登记的发票'); return }
  batchSaving.value = true
  let success = 0
  let failed = 0
  try {
    for (const row of readyRows) {
      row.status = 'SAVING'
      row.message = '正在登记…'
      try {
        const invoice = await purchaseInvoiceApi.registerBlue(row.command!)
        row.status = 'SUCCESS'
        row.invoiceNo = invoice.externalInvoiceNo || row.invoiceNo
        row.message = `登记成功，内部发票号 ${invoice.invoiceNo}`
        success += 1
      } catch (error) {
        row.status = 'FAILED'
        row.message = (error as Error).message
        failed += 1
      }
    }
    await reload()
    if (failed) message.warning(`批量登记完成：成功 ${success} 张，失败 ${failed} 张`)
    else message.success(`批量登记完成：成功 ${success} 张`)
  } finally {
    batchSaving.value = false
  }
}
async function extractPdf(file: File) {
  extracting.value = true
  try {
    const result = await purchaseInvoiceApi.extractPdf(file)
    pdfExtraction.value = result
    form.pdfFileName = result.fileName
    form.pdfStorageKey = result.storageKey
    form.extractedText = result.rawText || ''
    if (result.invoiceNo) form.externalInvoiceNo = result.invoiceNo
    if (result.invoiceType) form.invoiceType = result.invoiceType
    if (result.taxRate != null) form.taxRate = result.taxRate
    if (result.issuedAt) form.issuedAt = dayjs(result.issuedAt)
    if (result.amount != null) form.amount = Number(result.amount)
    const matchedSupplier = suppliers.value.find((item) =>
      (result.sellerTaxNo && item.taxNo && item.taxNo.toUpperCase() === result.sellerTaxNo.toUpperCase()) ||
      (result.sellerName && item.name && (item.name.includes(result.sellerName) || result.sellerName.includes(item.name))))
    if (matchedSupplier) {
      form.supplierId = matchedSupplier.id
      resetOrderSelection()
      if (result.amount != null) form.amount = Number(result.amount)
    }
    const vinOrderIds = orderIdsForVins(result.vinCandidates)
    const matchedOrder = purchaseOrders.value.find((order) =>
      result.orderNoCandidates.some((candidate) => candidate === order.externalOrderNo || candidate === order.orderNo))
      || (vinOrderIds.size === 1 ? purchaseOrders.value.find((order) =>
        vinOrderIds.has(order.id) && (!form.supplierId || order.supplierId === form.supplierId)) : undefined)
    if (matchedOrder) {
      const payable = payables.value.find((item) => item.supplierId === form.supplierId &&
        item.purchaseOrderId === matchedOrder.id)
      form.purchaseOrderId = payable?.purchaseOrderId || matchedOrder.id
      const availableVins = new Set(receipts.value
        .filter((receipt) => receipt.orderId === form.purchaseOrderId && receipt.status === 'POSTED')
        .flatMap((receipt) => receipt.lines.flatMap((line) => line.serialNos || [])))
      const matchedVins = result.vinCandidates.filter((vin) => availableVins.has(vin))
      form.serialNos = matchedVins.length ? matchedVins : [...result.vinCandidates]
      if (result.amount != null) form.amount = Number(result.amount)
    } else if (result.vinCandidates.length) {
      form.serialNos = [...result.vinCandidates]
    }
    if (result.type === 'RED') message.warning('识别为红字发票，请确认当前登记的是蓝字发票')
    else message.success('PDF 已识别，请核对票面字段后再登记')
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    extracting.value = false
  }
  return false
}

async function downloadPdf(record: PurchaseRedInvoice) {
  try {
    const blob = await purchaseInvoiceApi.downloadPdf(record.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = record.pdfFileName || `${record.invoiceNo}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) { message.error((error as Error).message) }
}

async function save() {
  if (!form.supplierId || !form.purchaseOrderId) { message.warning('请选择厂家和采购订单编号'); return }
  if (!form.issuedAt) { message.warning('请选择开票日期'); return }
  if (!form.serialNos?.length) { message.warning('请至少选择一个 VIN/SN'); return }
  if (!form.amount || Number(form.amount) <= 0) { message.warning('所选 VIN/SN 没有有效发票金额'); return }
  saving.value = true
  try {
    const invoice = await purchaseInvoiceApi.registerBlue({
      supplierId: form.supplierId,
      purchaseOrderId: form.purchaseOrderId,
      serialNos: form.serialNos,
      amount: form.amount,
      taxRate: form.taxRate,
      invoiceType: form.invoiceType || null,
      externalInvoiceNo: form.externalInvoiceNo || null,
      issuedAt: form.issuedAt ? dayjs(form.issuedAt).startOf('day').toDate().toISOString() : null,
      pdfFileName: form.pdfFileName || null,
      pdfStorageKey: form.pdfStorageKey || null,
      extractedText: form.extractedText || null,
      remark: form.remark || null,
    })
    message.success(`蓝字发票 ${invoice.invoiceNo} 已登记`)
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
async function autoMatch(record: PurchaseRedInvoice) {
  try {
    const result = await purchaseInvoiceApi.autoMatch(record.id)
    message.success(`已自动匹配 ${result.lines.length} 张应付，本次匹配 ${money(result.matchedNow)}`)
    await reload()
  } catch (error) { message.error((error as Error).message) }
}
async function reload() {
  try {
    const [invoiceRows, payableRows, receiptRows, orderRows] = await Promise.all([
      purchaseInvoiceApi.list(),
      payableApi.list(),
      purchaseApi.receipts(),
      purchaseApi.list(),
    ])
    allInvoices.value = invoiceRows
    rows.value = supplierId.value ? invoiceRows.filter((row) => row.supplierId === supplierId.value) : invoiceRows
    payables.value = payableRows
    receipts.value = receiptRows
    purchaseOrders.value = orderRows
  } catch (error) { message.error((error as Error).message) }
}
onMounted(async () => {
  try {
    const supplierRows = await supplierApi.list()
    suppliers.value = supplierRows
    supplierNames.value = Object.fromEntries(supplierRows.map((item) => [item.id, item.name]))
    supplierOptions.value = supplierRows.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.pdf-alert { margin: 12px 0; }
.linked-summary { margin-top: 4px; color: #4b5563; font-size: 12px; line-height: 18px; }
.batch-file-input { display: none; }
.batch-actions { margin-bottom: 14px; }
.batch-summary { color: #64748b; font-size: 13px; }
.batch-error { color: #cf1322; }
</style>
