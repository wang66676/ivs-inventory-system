<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="蓝字进项按供应商匹配应付，优先匹配到期日最早的未开票应付；红字进项仍从“红字发票”页面登记并入返利资金池。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">登记进项蓝字</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="supplierId" :options="supplierOptions" allow-clear show-search
                  placeholder="按厂家筛选" style="width:240px" @change="reload" />
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1500 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'RED' ? 'red' : 'blue'">{{ record.type === 'RED' ? '进项红字' : '进项蓝字' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'supplier'">{{ supplierName(record.supplierId) }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'tax'">{{ money(record.taxAmount) }}</template>
          <template v-else-if="column.key === 'matched'">{{ money(record.matchedAmount) }}</template>
          <template v-else-if="column.key === 'unmatched'">{{ money(record.unmatchedAmount) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDate(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button v-if="canWrite && record.type === 'BLUE' && Number(record.unmatchedAmount || 0) > 0"
                      size="small" type="primary" @click="autoMatch(record)">自动匹配应付</a-button>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="登记进项蓝字发票" :confirm-loading="saving" ok-text="登记蓝字"
             cancel-text="取消" width="680px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="OEM 厂家" required><a-select v-model:value="form.supplierId" :options="supplierOptions" show-search /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="发票金额" required><MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="税率"><a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="开票日期"><a-date-picker v-model:value="form.issuedAt" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="厂家外部票号"><a-input v-model:value="form.externalInvoiceNo" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { purchaseInvoiceApi } from '../../api/billing'
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { PurchaseRedInvoice } from '../../types/billing'

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_WRITE')
const rows = ref<PurchaseRedInvoice[]>([])
const supplierId = ref<string>()
const supplierOptions = ref<{ label: string; value: string }[]>([])
const supplierNames = ref<Record<string, string>>({})
const formOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, any>>({})
const columns = [
  { title: '内部发票号', key: 'invoiceNo', dataIndex: 'invoiceNo', width: 150 },
  { title: '类型', key: 'type', width: 100 },
  { title: '厂家', key: 'supplier', width: 190 },
  { title: '价税合计', key: 'amount', width: 140, align: 'right' },
  { title: '税额', key: 'tax', width: 130, align: 'right' },
  { title: '已匹配应付', key: 'matched', width: 140, align: 'right' },
  { title: '未匹配', key: 'unmatched', width: 140, align: 'right' },
  { title: '外部票号', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 160 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '登记日期', key: 'createdAt', width: 120 },
  { title: '操作', key: 'actions', width: 150, fixed: 'right' },
]
function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function supplierName(id: string) { return supplierNames.value[id] || id }
function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { amount: 0, taxRate: 0.13 })
  formOpen.value = true
}
async function save() {
  if (!form.supplierId || !form.amount || Number(form.amount) <= 0) { message.warning('请选择厂家并填写发票金额'); return }
  saving.value = true
  try {
    const invoice = await purchaseInvoiceApi.registerBlue({
      supplierId: form.supplierId, amount: form.amount, taxRate: form.taxRate,
      externalInvoiceNo: form.externalInvoiceNo || null,
      issuedAt: form.issuedAt ? form.issuedAt.toDate().toISOString() : null,
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
  try { rows.value = await purchaseInvoiceApi.list({ supplierId: supplierId.value }) }
  catch (error) { message.error((error as Error).message) }
}
onMounted(async () => {
  try {
    const suppliers = await supplierApi.list()
    supplierNames.value = Object.fromEntries(suppliers.map((item) => [item.id, item.name]))
    supplierOptions.value = suppliers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>
