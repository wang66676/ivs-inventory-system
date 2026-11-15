<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="销项发票支持上传 PDF 自动识别客户、销售订单、VIN/SN、发票类型、发票号码、发票金额、税率和开票日期。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">开票</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1760 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'serialNos'">
            <a-space v-if="record.serialNos?.length" wrap size="small">
              <a-tag v-for="serialNo in record.serialNos" :key="serialNo">{{ serialNo }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'invoiceType'">
            {{ record.type === 'RED' ? '红字发票' : (record.invoiceType || '-') }}
          </template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'taxRate'">{{ formatTaxRate(record.taxRate) }}</template>
          <template v-else-if="column.key === 'issuedAt'">{{ formatDate(record.issuedAt) }}</template>
          <template v-else-if="column.key === 'pdf'">
            <a v-if="record.pdfFileName" @click="downloadPdf(record)">{{ record.pdfFileName }}</a>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space size="small">
              <a v-if="canRedFlush && record.status === 'ISSUED' && record.type === 'BLUE'"
                 @click="redFlush(record)">红冲</a>
              <span v-if="!(canRedFlush && record.status === 'ISSUED' && record.type === 'BLUE')">-</span>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="登记销项蓝字发票" :confirm-loading="saving" ok-text="登记蓝字"
             cancel-text="取消" width="820px" @ok="save">
      <a-form layout="vertical">
        <a-upload :before-upload="extractPdf" :show-upload-list="false" accept=".pdf,application/pdf"
                  :disabled="extracting">
          <a-button :loading="extracting">上传销项发票 PDF 并自动识别</a-button>
        </a-upload>
        <a-alert v-if="pdfExtraction" class="pdf-alert" :type="pdfExtraction.textExtracted ? 'info' : 'warning'"
                 show-icon :message="extractionMessage" />
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="客户" required>
              <a-select v-model:value="form.customerId" :options="customerOptions" show-search allow-clear
                        placeholder="上传 PDF 自动匹配客户" @change="onCustomerChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="销售订单编号" required>
              <a-select v-model:value="form.orderId" :options="orderOptions" show-search allow-clear
                        :disabled="!form.customerId" placeholder="仅显示存在未开票 VIN/SN 的销售订单"
                        @change="onOrderChange" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="VIN/SN（可多选）" required>
              <a-select v-model:value="form.serialNos" mode="multiple" :options="serialOptions"
                        :disabled="!form.orderId" placeholder="上传 PDF 自动识别，或选择该订单已出库 VIN/SN"
                        @change="onSerialChange" />
              <div v-if="form.serialNos?.length" class="linked-summary">{{ form.serialNos.join('、') }}</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票类型" required>
              <a-input v-model:value="form.invoiceType" placeholder="上传 PDF 后读取“电子发票（…）”" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票号码" required>
              <a-input v-model:value="form.externalInvoiceNo" placeholder="与发票票面号码保持一致" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票金额（价税合计）" required>
              <MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" disabled style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="税率（如 13%）">
              <a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" :precision="4"
                              style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="开票日期" required>
              <a-date-picker v-model:value="form.issuedAt" format="YYYY-MM-DD" placeholder="选择年月日"
                             style="width:100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { customerApi, serialApi } from '../../api/masterdata'
import { invoiceApi, salesApi } from '../../api/sales'
import { useAuthStore } from '../../stores/auth'
import type { Customer, ProductSerial } from '../../types/masterdata'
import type {
  Invoice,
  SalesDelivery,
  SalesInvoicePdfExtraction,
  SalesOrder,
} from '../../types/sales'

interface DeliveredSerial {
  orderId: string
  serialNo: string
  unitPrice: number
}

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_WRITE')
const canRedFlush = auth.can('IVS_INVOICE_REDFLUSH')
const rows = ref<Invoice[]>([])
const customers = ref<Customer[]>([])
const serials = ref<ProductSerial[]>([])
const orders = ref<SalesOrder[]>([])
const deliveries = ref<SalesDelivery[]>([])
const formOpen = ref(false)
const saving = ref(false)
const extracting = ref(false)
const pdfExtraction = ref<SalesInvoicePdfExtraction>()
const form = reactive<Record<string, any>>({ taxRate: 0.13, serialNos: [] })

const columns = [
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 190 },
  { title: '销售订单编号', key: 'orderNo', dataIndex: 'orderNo', width: 180 },
  { title: 'VIN/SN', key: 'serialNos', width: 280 },
  { title: '发票类型', key: 'invoiceType', width: 220 },
  { title: '发票号码', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 190 },
  { title: '发票金额', key: 'amount', width: 140, align: 'right' },
  { title: '税率', key: 'taxRate', width: 100, align: 'right' },
  { title: '开票日期', key: 'issuedAt', width: 120, align: 'center' },
  { title: '电子发票', key: 'pdf', width: 180 },
  { title: '操作', key: 'actions', width: 100, fixed: 'right' },
]

const customerOptions = computed(() => customers.value.map((item) => ({
  label: `${item.name}（${item.code}）`,
  value: item.id,
})))

const serialNoById = computed(() => new Map(serials.value.map((item) => [item.id, item.serialNo])))

const deliveredSerials = computed<DeliveredSerial[]>(() => {
  const result: DeliveredSerial[] = []
  for (const delivery of deliveries.value) {
    if (delivery.status !== 'POSTED') continue
    for (const line of delivery.lines) {
      for (const serialId of line.serialIds || []) {
        const serialNo = serialNoById.value.get(serialId)
        if (!serialNo) continue
        result.push({ orderId: delivery.orderId, serialNo, unitPrice: Number(line.unitPrice || 0) })
      }
    }
  }
  return result
})

const invoicedSerialNosByOrder = computed(() => {
  const result = new Map<string, Set<string>>()
  for (const invoice of rows.value) {
    if (invoice.type !== 'BLUE' || invoice.status === 'VOID' || !invoice.orderId) continue
    const set = result.get(invoice.orderId) || new Set<string>()
    for (const serialNo of invoice.serialNos || []) set.add(serialNo.toUpperCase())
    result.set(invoice.orderId, set)
  }
  return result
})

const orderOptions = computed(() => {
  const result: { label: string; value: string }[] = []
  for (const order of orders.value) {
    if (order.status === 'DRAFT' || order.status === 'CANCELLED') continue
    if (form.customerId && order.customerId !== form.customerId) continue
    const invoiced = invoicedSerialNosByOrder.value.get(order.id) || new Set<string>()
    const available = deliveredSerials.value.filter((item) =>
      item.orderId === order.id && !invoiced.has(item.serialNo.toUpperCase()))
    if (!available.length) continue
    result.push({
      label: `${order.orderNo}（${order.customerName} / 可开票 ${available.length} 台）`,
      value: order.id,
    })
  }
  return result
})

const serialOptions = computed(() => {
  if (!form.orderId) return []
  const invoiced = invoicedSerialNosByOrder.value.get(form.orderId) || new Set<string>()
  const options = new Map<string, { label: string; value: string; amount: number }>()
  for (const item of deliveredSerials.value) {
    if (item.orderId !== form.orderId || invoiced.has(item.serialNo.toUpperCase())) continue
    if (!options.has(item.serialNo)) {
      options.set(item.serialNo, {
        label: `${item.serialNo}（${money(item.unitPrice)}）`,
        value: item.serialNo,
        amount: item.unitPrice,
      })
    }
  }
  return [...options.values()]
})

const extractionMessage = computed(() => {
  if (!pdfExtraction.value) return ''
  const customer = pdfExtraction.value.customerName || pdfExtraction.value.buyerName || '未匹配到客户'
  const order = pdfExtraction.value.orderNo || (pdfExtraction.value.orderNoCandidates || []).join('、') || '未匹配到销售订单'
  return `客户：${customer}；销售订单：${order}；${pdfExtraction.value.warning}`
})

function money(value?: number) {
  return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}
function formatDate(value?: string) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }
function formatTaxRate(value?: number) {
  return value == null ? '-' : `${(Number(value) * 100).toFixed(2).replace(/\.?0+$/, '')}%`
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { taxRate: 0.13, serialNos: [] })
  pdfExtraction.value = undefined
}
function openCreate() {
  resetForm()
  formOpen.value = true
}
function onCustomerChange() {
  form.orderId = undefined
  form.serialNos = []
  form.amount = undefined
}
function onOrderChange() {
  const order = orders.value.find((item) => item.id === form.orderId)
  if (order) form.customerId = order.customerId
  form.serialNos = []
  form.amount = undefined
}
function onSerialChange(serialNos: string[]) {
  const amounts = new Map(serialOptions.value.map((item) => [item.value, item.amount]))
  const selected = new Set(serialNos || [])
  const total = serialOptions.value
    .filter((item) => selected.has(item.value))
    .reduce((sum, item) => sum + Number(amounts.get(item.value) || 0), 0)
  form.amount = total > 0 ? Math.round(total * 100) / 100 : pdfExtraction.value?.amount
}

function matchCustomer(result: SalesInvoicePdfExtraction) {
  return customers.value.find((item) =>
    (result.buyerTaxNo && item.taxNo && item.taxNo.toUpperCase() === result.buyerTaxNo.toUpperCase()) ||
    (result.buyerName && item.name && (item.name.includes(result.buyerName) || result.buyerName.includes(item.name))))
}

function matchOrder(result: SalesInvoicePdfExtraction) {
  const direct = orders.value.find((item) => item.id === result.orderId)
  if (direct) return direct
  const byNo = orders.value.find((item) =>
    result.orderNoCandidates.some((candidate) => candidate === item.orderNo || candidate === item.externalOrderNo))
  if (byNo) return byNo
  const orderIds = new Set(deliveredSerials.value
    .filter((item) => result.vinCandidates.includes(item.serialNo))
    .map((item) => item.orderId))
  return orderIds.size === 1 ? orders.value.find((item) => orderIds.has(item.id)) : undefined
}

async function extractPdf(file: File) {
  extracting.value = true
  try {
    const result = await invoiceApi.extractPdf(file)
    pdfExtraction.value = result
    form.pdfFileName = result.fileName
    form.pdfStorageKey = result.storageKey
    form.extractedText = result.rawText || ''
    if (result.invoiceNo) form.externalInvoiceNo = result.invoiceNo
    if (result.invoiceType) form.invoiceType = result.invoiceType
    if (result.taxRate != null) form.taxRate = result.taxRate
    if (result.issuedAt) form.issuedAt = dayjs(result.issuedAt)
    if (result.amount != null) form.amount = Number(result.amount)

    const matchedCustomer = matchCustomer(result)
    const matchedOrder = matchOrder(result)
    if (matchedOrder) {
      form.customerId = matchedOrder.customerId
      form.orderId = matchedOrder.id
      const available = new Set(deliveredSerials.value
        .filter((item) => item.orderId === matchedOrder.id)
        .map((item) => item.serialNo))
      const matchedVins = result.vinCandidates.filter((vin) => available.has(vin))
      form.serialNos = matchedVins.length ? matchedVins : [...result.vinCandidates]
    } else {
      if (matchedCustomer) form.customerId = matchedCustomer.id
      if (result.vinCandidates.length) form.serialNos = [...result.vinCandidates]
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

async function save() {
  if (!form.customerId || !form.orderId) { message.warning('请选择客户和销售订单编号'); return }
  if (!form.serialNos?.length) { message.warning('请至少选择一个 VIN/SN'); return }
  if (!form.invoiceType) { message.warning('请填写发票类型'); return }
  if (!form.externalInvoiceNo) { message.warning('请填写发票号码'); return }
  if (!form.amount || Number(form.amount) <= 0) { message.warning('发票金额必须大于 0'); return }
  if (!form.issuedAt) { message.warning('请选择开票日期'); return }
  saving.value = true
  try {
    const invoice = await invoiceApi.registerBlue({
      customerId: form.customerId,
      orderId: form.orderId,
      serialNos: form.serialNos,
      invoiceType: form.invoiceType,
      externalInvoiceNo: form.externalInvoiceNo,
      amount: form.amount,
      taxRate: form.taxRate,
      issuedAt: dayjs(form.issuedAt).startOf('day').toDate().toISOString(),
      pdfFileName: form.pdfFileName || null,
      pdfStorageKey: form.pdfStorageKey || null,
      extractedText: form.extractedText || null,
    })
    message.success(`销项蓝字发票 ${invoice.invoiceNo} 已登记`)
    formOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
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

async function redFlush(record: Invoice) {
  try {
    const red = await invoiceApi.redFlush(record.id)
    message.success(`已红冲，生成红字发票 ${red.invoiceNo}`)
    await reload()
  } catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try { rows.value = await invoiceApi.list() }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [customerRows, serialRows, orderRows, deliveryRows] = await Promise.all([
      customerApi.list(),
      serialApi.list(),
      salesApi.orders(),
      salesApi.deliveries(),
    ])
    customers.value = customerRows
    serials.value = serialRows
    orders.value = orderRows
    deliveries.value = deliveryRows
  } catch {
    // 开票数据仍可加载，缺少主数据时由表单提示用户检查权限或数据。
  }
  await reload()
})
</script>

<style scoped>
.pdf-alert { margin: 12px 0; }
.linked-summary { margin-top: 4px; color: #4b5563; font-size: 12px; line-height: 18px; }
</style>
