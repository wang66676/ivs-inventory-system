<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="厂家红字发票登记后自动进入返利资金池。关联申报单时，红字金额必须等于厂家确认额，登记成功后申报单与权益同步结算。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">登记厂家红字</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="supplierId" :options="supplierOptions" allow-clear show-search
                  placeholder="按厂家筛选" style="width:240px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1800 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'supplier'">{{ supplierName(record.supplierId) }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'tax'">{{ money(record.taxAmount) }}（{{ rate(record.taxRate) }}）</template>
          <template v-else-if="column.key === 'type'"><a-tag color="red">进项红字</a-tag></template>
          <template v-else-if="column.key === 'claim'">
            <span v-if="record.claimId">{{ claimLabel(record.claimId) }}</span>
            <span v-else class="sub">未关联申报单</span>
          </template>
          <template v-else-if="column.key === 'issuedAt'">{{ formatTime(record.issuedAt) }}</template>
          <template v-else-if="column.key === 'pdf'">
            <a-button v-if="record.pdfFileName" type="link" size="small" style="padding:0" @click="downloadPdf(record)">
              {{ record.pdfFileName }}
            </a-button>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="登记厂家红字发票" :confirm-loading="saving" ok-text="登记并自动入池"
             cancel-text="取消" width="680px" @ok="save">
      <a-form layout="vertical">
        <a-upload :before-upload="extractPdf" :show-upload-list="false" accept=".pdf,application/pdf"
                  :disabled="extracting">
          <a-button :loading="extracting">上传电子发票 PDF 并自动识别</a-button>
        </a-upload>
        <a-alert v-if="pdfExtraction" class="pdf-alert" :type="pdfExtraction.textExtracted ? 'info' : 'warning'"
                 show-icon :message="pdfExtraction.warning" />
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="关联申报单">
              <a-select v-model:value="form.claimId" :options="claimOptions" allow-clear show-search
                        placeholder="可选：选择后自动带出确认额" @change="onClaimChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="OEM 厂家" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="红字金额" required>
              <MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="税率"><a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="开票时间" required><a-date-picker v-model:value="form.issuedAt" show-time format="YYYY-MM-DD HH:mm" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="原蓝票号"><a-input v-model:value="form.originalInvoiceNo" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="厂家外部票号"><a-input v-model:value="form.externalInvoiceNo" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { purchaseInvoiceApi } from '../../api/billing'
import { policyApi } from '../../api/policy'
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { OemClaim } from '../../types/policy'
import type { PurchaseInvoicePdfExtraction, PurchaseRedInvoice } from '../../types/billing'
import type { Supplier } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_REDFLUSH')
const rows = ref<PurchaseRedInvoice[]>([])
const claims = ref<OemClaim[]>([])
const supplierId = ref<string | undefined>()
const supplierOptions = ref<{ label: string; value: string }[]>([])
const supplierNames = ref<Record<string, string>>({})
const suppliers = ref<Supplier[]>([])
const extracting = ref(false)
const pdfExtraction = ref<PurchaseInvoicePdfExtraction>()
const formOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, any>>({})

const claimOptions = computed(() => claims.value.filter((claim) => ["CONFIRMED", "PARTIALLY_CONFIRMED"].includes(claim.status)).map((claim) => ({
  label: `${claim.claimNo} · ${claim.supplierName || ''} · ${money(claim.confirmedAmount)}`,
  value: claim.id,
})))
const columns = [
  { title: '内部发票号', key: 'invoiceNo', dataIndex: 'invoiceNo', width: 150 },
  { title: '类型', key: 'type', width: 100 },
  { title: '厂家', key: 'supplier', width: 190 },
  { title: '金额', key: 'amount', width: 130 },
  { title: '税额', key: 'tax', width: 170 },
  { title: '原蓝票号', key: 'originalInvoiceNo', dataIndex: 'originalInvoiceNo', width: 150 },
  { title: '外部票号', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 150 },
  { title: '关联申报单', key: 'claim', width: 180 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '开票时间', key: 'issuedAt', width: 165 },
  { title: '电子发票', key: 'pdf', width: 180 },
  { title: '登记时间', key: 'createdAt', width: 170 },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function rate(value?: number) { return value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%` }
function supplierName(id: string) { return supplierNames.value[id] || id }
function claimLabel(id: string) { return claims.value.find((claim) => claim.id === id)?.claimNo || id }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { taxRate: 0.13, amount: 0, issuedAt: dayjs() })
  pdfExtraction.value = undefined
  formOpen.value = true
}

function onClaimChange(claimId?: string) {
  const claim = claims.value.find((item) => item.id === claimId)
  if (!claim) return
  form.supplierId = claim.supplierId
  form.amount = Number(claim.confirmedAmount)
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
    if (result.amount != null) form.amount = Number(result.amount)
    if (result.taxRate != null) form.taxRate = result.taxRate
    if (result.issuedAt) form.issuedAt = dayjs(result.issuedAt)
    const matchedSupplier = suppliers.value.find((item) =>
      (result.sellerTaxNo && item.taxNo && item.taxNo.toUpperCase() === result.sellerTaxNo.toUpperCase()) ||
      (result.sellerName && item.name && (item.name.includes(result.sellerName) || result.sellerName.includes(item.name))))
    if (matchedSupplier) form.supplierId = matchedSupplier.id
    if (result.type === 'BLUE') message.warning('识别为蓝字发票，请确认当前登记的是红字发票')
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
  if (!form.supplierId || !form.amount || Number(form.amount) <= 0) {
    message.warning('请选择厂家并填写红字金额'); return
  }
  if (!form.issuedAt) { message.warning('请选择开票时间'); return }
  if (form.claimId) {
    const claim = claims.value.find((item) => item.id === form.claimId)
    if (claim && Number(form.amount) !== Number(claim.confirmedAmount)) {
      message.warning(`红字金额必须等于厂家确认额 ${money(claim.confirmedAmount)}`); return
    }
  }
  saving.value = true
  try {
    const invoice = await purchaseInvoiceApi.registerRed({
      supplierId: form.supplierId, amount: form.amount, taxRate: form.taxRate,
      originalInvoiceNo: form.originalInvoiceNo || null, externalInvoiceNo: form.externalInvoiceNo || null,
      claimId: form.claimId || null,
      issuedAt: dayjs(form.issuedAt).toDate().toISOString(),
      pdfFileName: form.pdfFileName || null,
      pdfStorageKey: form.pdfStorageKey || null,
      extractedText: form.extractedText || null,
      remark: form.remark || null,
    })
    message.success(`红字 ${invoice.invoiceNo} 已登记并入池 ${money(invoice.amount)}`)
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function reload() {
  try {
    const [invoiceRows, claimRows] = await Promise.all([
      purchaseInvoiceApi.list({ supplierId: supplierId.value }),
      policyApi.claims(),
    ])
    rows.value = invoiceRows
    claims.value = claimRows
  } catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const supplierRows = await supplierApi.list({ supplierType: 'OEM' })
    suppliers.value = supplierRows
    supplierNames.value = Object.fromEntries(supplierRows.map((item) => [item.id, item.name]))
    supplierOptions.value = supplierRows.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.sub { color: #8b96a8; font-size: 12px; }
.pdf-alert { margin: 12px 0; }
</style>

