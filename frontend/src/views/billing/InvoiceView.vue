<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="销项发票支持上传 PDF 自动识别发票号码、发票类型、金额、税率和开票日期，并按 VIN/SN 自动关联对应的销售订单编号。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">开票</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1580 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'RED' ? 'red' : 'blue'">{{ typeLabel(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'taxAmount'">{{ money(record.taxAmount) }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'issuedAt'">{{ formatDate(record.issuedAt) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDate(record.createdAt) }}</template>
          <template v-else-if="column.key === 'pdf'">
            <a v-if="record.pdfFileName" @click="downloadPdf(record)">{{ record.pdfFileName }}</a>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a v-if="canWrite && record.status === 'DRAFT'" @click="openIssue(record)">开具</a>
              <a v-if="canWrite && record.status === 'DRAFT'" @click="voidInvoice(record)">作废</a>
              <a v-if="canRedFlush && record.status === 'ISSUED' && record.type === 'BLUE'"
                 @click="redFlush(record)">红冲</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="createOpen" title="开票" :confirm-loading="saving" ok-text="创建草稿" cancel-text="取消"
             width="700px" @ok="submit">
      <a-form layout="vertical">
        <a-upload :before-upload="extractPdf" :show-upload-list="false" accept=".pdf,application/pdf" :disabled="extracting">
          <a-button :loading="extracting">上传销项发票 PDF 并自动识别</a-button>
        </a-upload>
        <a-alert v-if="lastExtraction" class="pdf-alert" :type="lastExtraction.textExtracted ? 'info' : 'warning'"
                 show-icon :message="extractionMessage" />
        <a-form-item label="销售订单" required>
          <a-select v-model:value="form.orderId" :options="orderOptions" show-search @change="onOrderChange" />
        </a-form-item>
        <a-form-item label="开票金额（价税合计，留空取订单已出库金额）">
          <MoneyInput v-model:value="form.amount" :min="0" :precision="2" style="width:100%" />
        </a-form-item>
        <a-form-item label="税率（如 0.13）">
          <a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" />
        </a-form-item>
        <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="issueOpen" title="开具发票" :confirm-loading="saving" ok-text="确认开具" cancel-text="取消"
             width="650px" @ok="submitIssue">
      <a-form layout="vertical">
        <a-upload :before-upload="extractPdf" :show-upload-list="false" accept=".pdf,application/pdf" :disabled="extracting">
          <a-button :loading="extracting">上传 PDF 识别并回填</a-button>
        </a-upload>
        <a-alert v-if="lastExtraction" class="pdf-alert" :type="lastExtraction.textExtracted ? 'info' : 'warning'"
                 show-icon :message="extractionMessage" />
        <a-form-item label="销售订单编号"><a-input :value="currentInvoice?.orderNo" disabled /></a-form-item>
        <a-form-item label="发票号码" required><a-input v-model:value="issueForm.externalInvoiceNo" /></a-form-item>
        <a-form-item label="发票类型"><a-input v-model:value="issueForm.invoiceType" placeholder="如：电子发票（增值税专用发票）" /></a-form-item>
        <a-form-item label="开票日期" required>
          <a-date-picker v-model:value="issueForm.issuedAt" format="YYYY-MM-DD" placeholder="选择年月日" style="width:100%" />
        </a-form-item>
        <a-form-item label="税率（可留空保持原值）">
          <a-input-number v-model:value="issueForm.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { invoiceApi, salesApi } from '../../api/sales'
import { useAuthStore } from '../../stores/auth'
import { INVOICE_STATUS_COLORS, INVOICE_STATUS_LABELS, INVOICE_TYPE_LABELS } from '../../types/sales'
import type { Invoice, InvoiceStatus, InvoiceType, SalesInvoicePdfExtraction } from '../../types/sales'

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_WRITE')
const canRedFlush = auth.can('IVS_INVOICE_REDFLUSH')
const rows = ref<Invoice[]>([])
const orderOptions = ref<{ label: string; value: string }[]>([])
const createOpen = ref(false)
const issueOpen = ref(false)
const saving = ref(false)
const extracting = ref(false)
const currentInvoice = ref<Invoice | null>(null)
const lastExtraction = ref<SalesInvoicePdfExtraction>()
const form = reactive<Record<string, any>>({ taxRate: 0.13 })
const issueForm = reactive<Record<string, any>>({})
const columns = [
  { title: '内部发票号', key: 'invoiceNo', dataIndex: 'invoiceNo', width: 140 },
  { title: '类型', key: 'type', width: 90 },
  { title: '销售订单编号', key: 'orderNo', dataIndex: 'orderNo', width: 160 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 190 },
  { title: '价税合计', key: 'amount', width: 130, align: 'right' },
  { title: '税额', key: 'taxAmount', width: 120, align: 'right' },
  { title: '发票类型', key: 'invoiceType', dataIndex: 'invoiceType', width: 220 },
  { title: '发票号码', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 180 },
  { title: '状态', key: 'status', width: 100 },
  { title: '开票日期', key: 'issuedAt', width: 110 },
  { title: '电子发票', key: 'pdf', width: 180 },
  { title: '登记日期', key: 'createdAt', width: 110 },
  { title: '操作', key: 'actions', width: 130, fixed: 'right' },
]
const extractionMessage = computed(() => {
  if (!lastExtraction.value) return ''
  const order = lastExtraction.value.orderNo ? `销售订单：${lastExtraction.value.orderNo}` : '未匹配到销售订单'
  return `${order}；${lastExtraction.value.warning}`
})
const statusLabel = (status: string) => INVOICE_STATUS_LABELS[status as InvoiceStatus]
const statusColor = (status: string) => INVOICE_STATUS_COLORS[status as InvoiceStatus]
const typeLabel = (type: string) => INVOICE_TYPE_LABELS[type as InvoiceType]
function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

async function reload() {
  try { rows.value = await invoiceApi.list() }
  catch (error) { message.error((error as Error).message) }
}
function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { taxRate: 0.13 })
  lastExtraction.value = undefined
  createOpen.value = true
}
function onOrderChange() {
  form.amount = lastExtraction.value?.orderId === form.orderId ? lastExtraction.value?.amount : undefined
}
async function extractPdf(file: File) {
  extracting.value = true
  try {
    const result = await invoiceApi.extractPdf(file)
    lastExtraction.value = result
    const target = issueOpen.value ? issueForm : form
    if (result.invoiceNo) target.externalInvoiceNo = result.invoiceNo
    if (result.invoiceType) target.invoiceType = result.invoiceType
    if (result.taxRate != null) target.taxRate = result.taxRate
    if (result.issuedAt) target.issuedAt = dayjs(result.issuedAt)
    if (result.amount != null) target.amount = Number(result.amount)
    target.pdfFileName = result.fileName
    target.pdfStorageKey = result.storageKey
    target.extractedText = result.rawText || ''
    if (createOpen.value && result.orderId) form.orderId = result.orderId
    if (issueOpen.value && result.orderId && currentInvoice.value?.orderId && result.orderId !== currentInvoice.value.orderId) {
      message.warning('PDF 识别到的销售订单与当前发票草稿不一致，请核对')
    }
    if (result.type === 'RED') message.warning('识别为红字发票，请核对当前操作')
    else message.success('销项发票 PDF 已识别，请核对后开具')
  } catch (error) { message.error((error as Error).message) }
  finally { extracting.value = false }
  return false
}
async function submit() {
  if (!form.orderId) { message.warning('请选择销售订单'); return }
  saving.value = true
  try {
    const payload: Record<string, unknown> = { orderId: form.orderId, taxRate: form.taxRate, remark: form.remark }
    if (form.amount) payload.amount = form.amount
    const created = await invoiceApi.create(payload)
    createOpen.value = false
    message.success('发票草稿已创建')
    openIssue(created)
    await reload()
  } catch (error) { message.error((error as Error).message) }
  finally { saving.value = false }
}
function openIssue(record: Invoice) {
  currentInvoice.value = record
  Object.keys(issueForm).forEach((key) => delete issueForm[key])
  Object.assign(issueForm, {
    taxRate: record.taxRate,
    issuedAt: record.issuedAt ? dayjs(record.issuedAt) : dayjs(),
    invoiceType: record.invoiceType || '',
    externalInvoiceNo: record.externalInvoiceNo || '',
  })
  if (lastExtraction.value && (!lastExtraction.value.orderId || lastExtraction.value.orderId === record.orderId)) {
    if (lastExtraction.value.invoiceNo) issueForm.externalInvoiceNo = lastExtraction.value.invoiceNo
    if (lastExtraction.value.invoiceType) issueForm.invoiceType = lastExtraction.value.invoiceType
    if (lastExtraction.value.issuedAt) issueForm.issuedAt = dayjs(lastExtraction.value.issuedAt)
    issueForm.pdfFileName = lastExtraction.value.fileName
    issueForm.pdfStorageKey = lastExtraction.value.storageKey
    issueForm.extractedText = lastExtraction.value.rawText || ''
  }
  issueOpen.value = true
}
async function submitIssue() {
  if (!currentInvoice.value) return
  if (!issueForm.externalInvoiceNo) { message.warning('请填写发票号码'); return }
  if (!issueForm.issuedAt) { message.warning('请选择开票日期'); return }
  saving.value = true
  try {
    await invoiceApi.issue(currentInvoice.value.id, {
      ...issueForm,
      issuedAt: dayjs(issueForm.issuedAt).startOf('day').toDate().toISOString(),
    })
    message.success('发票已开具')
    issueOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) }
  finally { saving.value = false }
}
async function downloadPdf(record: Invoice) {
  try {
    const blob = await invoiceApi.downloadPdf(record.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = record.pdfFileName || `${record.externalInvoiceNo || record.invoiceNo}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) { message.error((error as Error).message) }
}
async function voidInvoice(record: Invoice) {
  try { await invoiceApi.void(record.id); message.success('发票已作废'); await reload() }
  catch (error) { message.error((error as Error).message) }
}
async function redFlush(record: Invoice) {
  try {
    const red = await invoiceApi.redFlush(record.id)
    message.success(`已红冲，生成红字发票 ${red.invoiceNo}`)
    await reload()
  } catch (error) { message.error((error as Error).message) }
}
onMounted(async () => {
  try {
    const orders = await salesApi.orders()
    orderOptions.value = orders
      .filter((item) => item.status !== 'DRAFT' && item.status !== 'CANCELLED')
      .map((item) => ({ label: `${item.orderNo}（${item.customerName} / ${item.totalAmount}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.pdf-alert { margin: 12px 0; }
</style>