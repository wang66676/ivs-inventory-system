<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="蓝字进项按厂家选择未开票采购订单，并按 VIN/SN 数量累计发票金额；登记后可继续匹配应付。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">登记进项蓝字</a-button>
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
             cancel-text="取消" width="760px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="OEM 厂家" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search
                        @change="onSupplierChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="采购订单编号" required>
              <a-select v-model:value="form.purchaseOrderId" :options="orderOptions" show-search allow-clear
                        :disabled="!form.supplierId" placeholder="仅显示未开票订单" @change="onOrderChange" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="VIN / SN（可多选）" required>
              <a-select v-model:value="form.serialNos" mode="multiple" :options="serialOptions"
                        :disabled="!form.purchaseOrderId" placeholder="选择后自动累计发票金额"
                        @change="onSerialChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发票金额（按 VIN/SN 自动累计）" required>
              <MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" disabled style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="税率"><a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="开票日期"><a-date-picker v-model:value="form.issuedAt" format="YYYY-MM-DD" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="厂家外部票号"><a-input v-model:value="form.externalInvoiceNo" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { purchaseInvoiceApi } from '../../api/billing'
import { purchaseApi } from '../../api/procurement'
import { payableApi } from '../../api/payable'
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { PurchaseRedInvoice } from '../../types/billing'
import type { Payable } from '../../types/payable'
import type { PurchaseReceipt } from '../../types/procurement'

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_WRITE')
const rows = ref<PurchaseRedInvoice[]>([])
const allInvoices = ref<PurchaseRedInvoice[]>([])
const payables = ref<Payable[]>([])
const receipts = ref<PurchaseReceipt[]>([])
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
  { title: '采购订单编号', key: 'purchaseOrder', width: 170 },
  { title: 'VIN / SN', key: 'serialNos', width: 260 },
  { title: '价税合计', key: 'amount', width: 140, align: 'right' },
  { title: '税额', key: 'tax', width: 130, align: 'right' },
  { title: '已匹配应付', key: 'matched', width: 140, align: 'right' },
  { title: '未匹配', key: 'unmatched', width: 140, align: 'right' },
  { title: '外部票号', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 160 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '登记日期', key: 'createdAt', width: 120 },
  { title: '操作', key: 'actions', width: 150, fixed: 'right' },
]

const orderNoById = computed(() => Object.fromEntries(
  payables.value.map((item) => [item.purchaseOrderId || '', item.orderNo || '']),
))
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
  if (!form.purchaseOrderId) return []
  const invoiced = new Set(allInvoices.value
    .filter((row) => row.type === 'BLUE' && row.purchaseOrderId === form.purchaseOrderId)
    .flatMap((row) => row.serialNos || []))
  const options: { label: string; value: string; amount: number }[] = []
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
  return options
})

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function supplierName(id: string) { return supplierNames.value[id] || id }
function resetOrderSelection() {
  form.purchaseOrderId = undefined
  form.serialNos = []
  form.amount = 0
}
function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { amount: 0, taxRate: 0.13, serialNos: [] })
  formOpen.value = true
}
function onSupplierChange() {
  resetOrderSelection()
}
function onOrderChange() {
  form.serialNos = []
  form.amount = 0
}
function onSerialChange(serialNos: string[]) {
  const selected = new Set(serialNos || [])
  form.amount = Math.round(serialOptions.value
    .filter((item) => selected.has(item.value))
    .reduce((sum, item) => sum + item.amount, 0) * 100) / 100
}
async function save() {
  if (!form.supplierId || !form.purchaseOrderId) { message.warning('请选择厂家和采购订单编号'); return }
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
  try {
    const [invoiceRows, payableRows, receiptRows] = await Promise.all([
      purchaseInvoiceApi.list(),
      payableApi.list(),
      purchaseApi.receipts(),
    ])
    allInvoices.value = invoiceRows
    rows.value = supplierId.value ? invoiceRows.filter((row) => row.supplierId === supplierId.value) : invoiceRows
    payables.value = payableRows
    receipts.value = receiptRows
  } catch (error) { message.error((error as Error).message) }
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
