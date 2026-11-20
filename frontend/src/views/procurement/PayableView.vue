<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="采购收货生成应付；付款按应付单核销；采购退货会按到期日顺序冲减同一供应商的未付应付。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="supplierId" :options="supplierOptions" allow-clear show-search
                  placeholder="按供应商筛选" style="width:240px" @change="reload" />
        <a-select v-model:value="status" :options="statusOptions" allow-clear
                  placeholder="按状态筛选" style="width:180px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1650 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'purchaseQuantity'">{{ quantity(record.purchaseQuantity) }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'rebateCredit'">{{ money(record.rebateCreditAmount) }}</template>
          <template v-else-if="column.key === 'netAmount'">{{ money(record.netAmount) }}</template>
          <template v-else-if="column.key === 'invoiced'">{{ money(record.invoiceMatchedAmount) }}</template>
          <template v-else-if="column.key === 'uninvoiced'">{{ money(record.uninvoicedAmount) }}</template>
          <template v-else-if="column.key === 'paid'">{{ money(record.paidAmount) }}</template>
          <template v-else-if="column.key === 'outstanding'">{{ money(record.outstanding) }}</template>
          <template v-else-if="column.key === 'lastPaymentMethod'">{{ paymentMethodLabel(record.lastPaymentMethod) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'SETTLED' ? 'green' : record.status === 'PARTIALLY_PAID' ? 'orange' : 'red'">
              {{ PAYABLE_STATUS_LABELS[record.status as PayableStatus] }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button v-if="record.status !== 'SETTLED'" size="small" type="primary" @click="openPay(record)">付款</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
    <a-modal v-model:open="payOpen" title="应付付款" :confirm-loading="saving" ok-text="确认付款" cancel-text="取消" @ok="submitPay">
      <a-form layout="vertical">
        <a-form-item label="应付单">{{ current?.payableNo }}</a-form-item>
        <a-form-item label="未付金额">{{ money(current?.outstanding) }}</a-form-item>
        <a-form-item label="本次付款" required><MoneyInput v-model:value="payAmount" :min="0.01" :max="current?.outstanding" :precision="2" style="width:100%" /></a-form-item>
        <a-form-item label="付款时间" required>
          <a-date-picker v-model:value="payDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" />
        </a-form-item>
        <a-form-item label="付款方式" required>
          <a-select v-model:value="paymentMethod" :options="paymentMethodOptions" style="width:100%" />
        </a-form-item>
        <a-form-item label="备注"><a-input v-model:value="payRemark" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { payableApi } from '../../api/payable'
import { purchaseApi } from '../../api/procurement'
import { supplierApi } from '../../api/masterdata'
import { PAYABLE_PAYMENT_METHOD_LABELS, PAYABLE_STATUS_LABELS } from '../../types/payable'
import type { Payable, PayablePaymentMethod, PayableStatus } from '../../types/payable'
import type { BusinessScope } from '../../utils/businessScope'

const props = withDefaults(defineProps<{ scope?: BusinessScope }>(), { scope: 'VEHICLE' })
const rows = ref<Payable[]>([])
const supplierId = ref<string | undefined>()
const status = ref<PayableStatus | undefined>()
const supplierOptions = ref<{ label: string; value: string }[]>([])
const statusOptions = Object.entries(PAYABLE_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const paymentMethodOptions = Object.entries(PAYABLE_PAYMENT_METHOD_LABELS).map(([value, label]) => ({ value, label }))
const payOpen = ref(false)
const saving = ref(false)
const current = ref<Payable>()
const payAmount = ref<number>()
const payRemark = ref('')
const payDate = ref(dayjs().format('YYYY-MM-DD'))
const paymentMethod = ref<PayablePaymentMethod>('CASH')
const columns = [
  { title: '应付单号', key: 'payableNo', dataIndex: 'payableNo', width: 140 },
  { title: '供应商', key: 'supplierName', dataIndex: 'supplierName', width: 190 },
  { title: '采购订单编号', key: 'orderNo', dataIndex: 'orderNo', width: 160 },
  { title: '采购数量', key: 'purchaseQuantity', width: 110, align: 'right' },
  { title: '采购应付', key: 'amount', width: 130 },
  { title: '返利冲减', key: 'rebateCredit', width: 130 },
  { title: '应付净额', key: 'netAmount', width: 130 },
  { title: '已开票', key: 'invoiced', width: 130 },
  { title: '未开票', key: 'uninvoiced', width: 130 },
  { title: '已付', key: 'paid', width: 130 },
  { title: '未付', key: 'outstanding', width: 130 },
  { title: '到期日', key: 'dueDate', dataIndex: 'dueDate', width: 110 },
  { title: '最近付款日', key: 'lastPaymentDate', dataIndex: 'lastPaymentDate', width: 120 },
  { title: '最近付款方式', key: 'lastPaymentMethod', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 100 },
]
function money(v?: number) { return `¥${Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function quantity(v?: number) { return Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 }) }
function formatTime(v: string) { return v ? v.replace('T', ' ').slice(0, 19) : '-' }
function paymentMethodLabel(value?: string) { return PAYABLE_PAYMENT_METHOD_LABELS[value as PayablePaymentMethod] || value || '-' }
function openPay(record: Payable) {
  current.value = record
  payAmount.value = record.outstanding
  payRemark.value = ''
  payDate.value = dayjs().format('YYYY-MM-DD')
  paymentMethod.value = 'CASH'
  payOpen.value = true
}
async function submitPay() {
  if (!current.value || !payAmount.value) { message.warning('请填写付款金额'); return }
  if (!payDate.value) { message.warning('请选择付款时间'); return }
  if (!paymentMethod.value) { message.warning('请选择付款方式'); return }
  saving.value = true
  try {
    await payableApi.pay(current.value.id, {
      amount: payAmount.value,
      remark: payRemark.value || null,
      paymentDate: payDate.value,
      paymentMethod: paymentMethod.value,
    })
    message.success('付款已核销')
    payOpen.value = false
    await reload()
  } catch (e) { message.error((e as Error).message) } finally { saving.value = false }
}
async function reload() {
  try {
    const [payableRows, orders] = await Promise.all([
      payableApi.list({ supplierId: supplierId.value, status: status.value }),
      purchaseApi.list(),
    ])
    const scopedOrderIds = new Set(orders
      .filter((order) => (order.businessScope || 'VEHICLE') === props.scope)
      .map((order) => order.id))
    rows.value = payableRows.filter((row) => row.purchaseOrderId
      ? scopedOrderIds.has(row.purchaseOrderId)
      : props.scope === 'VEHICLE')
  } catch (e) { message.error((e as Error).message) }
}
onMounted(async () => {
  try {
    const suppliers = await supplierApi.list()
    supplierOptions.value = suppliers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>
