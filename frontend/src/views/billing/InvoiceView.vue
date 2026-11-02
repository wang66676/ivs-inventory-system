<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="销项发票：一期整车按全价开票（优惠由 M4 的政策返利承接）。红冲会生成一张关联原蓝票的红字发票，并把原票置为「已红冲」；税额按含税金额与税率反算。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">开票</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1200 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'RED' ? 'red' : 'blue'">{{ typeLabel(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'taxAmount'">
            {{ record.taxAmount ?? '-' }}
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ String(record.createdAt).replace('T', ' ').slice(0, 19) }}
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
             width="620px" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="销售订单" required>
          <a-select v-model:value="form.orderId" :options="orderOptions" show-search
                    @change="onOrderChange" />
        </a-form-item>
        <a-form-item label="开票金额（留空取该订单已出库金额）">
          <MoneyInput v-model:value="form.amount" :min="0" :precision="2" style="width:100%" />
        </a-form-item>
        <a-form-item label="税率（如 0.13）">
          <a-input-number v-model:value="form.taxRate" :min="0" :max="1" :step="0.01" style="width:100%" />
        </a-form-item>
        <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="issueOpen" title="开具发票" :confirm-loading="saving" ok-text="确认开具" cancel-text="取消"
             width="520px" @ok="submitIssue">
      <a-form layout="vertical">
        <a-form-item label="外部发票号（金税系统开具后回填）" required>
          <a-input v-model:value="issueForm.externalInvoiceNo" />
        </a-form-item>
        <a-form-item label="税率（可留空保持原值）">
          <a-input-number v-model:value="issueForm.taxRate" :min="0" :max="1" :step="0.01" style="width:100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { invoiceApi, salesApi } from '../../api/sales'
import { useAuthStore } from '../../stores/auth'
import { INVOICE_STATUS_COLORS, INVOICE_STATUS_LABELS, INVOICE_TYPE_LABELS } from '../../types/sales'
import type { Invoice, InvoiceStatus, InvoiceType } from '../../types/sales'

const auth = useAuthStore()
const canWrite = auth.can('IVS_INVOICE_WRITE')
const canRedFlush = auth.can('IVS_INVOICE_REDFLUSH')

const rows = ref<Invoice[]>([])
const orderOptions = ref<{ label: string; value: string }[]>([])
const createOpen = ref(false)
const issueOpen = ref(false)
const saving = ref(false)
const currentInvoice = ref<Invoice | null>(null)
const form = reactive<Record<string, any>>({ taxRate: 0.13 })
const issueForm = reactive<Record<string, any>>({})

const columns = [
  { title: '发票号', key: 'invoiceNo', dataIndex: 'invoiceNo', width: 150 },
  { title: '类型', key: 'type', width: 110 },
  { title: '订单号', key: 'orderNo', dataIndex: 'orderNo', width: 150 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 200 },
  { title: '金额', key: 'amount', dataIndex: 'amount', width: 140 },
  { title: '税额', key: 'taxAmount', width: 130 },
  { title: '外部票号', key: 'externalInvoiceNo', dataIndex: 'externalInvoiceNo', width: 150 },
  { title: '状态', key: 'status', width: 110 },
  { title: '创建时间', key: 'createdAt', width: 180 },
  { title: '操作', key: 'actions', width: 160 },
]

const statusLabel = (status: string) => INVOICE_STATUS_LABELS[status as InvoiceStatus]
const statusColor = (status: string) => INVOICE_STATUS_COLORS[status as InvoiceStatus]
const typeLabel = (type: string) => INVOICE_TYPE_LABELS[type as InvoiceType]

async function reload() {
  try {
    rows.value = await invoiceApi.list()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  form.taxRate = 0.13
  createOpen.value = true
}

function onOrderChange() {
  form.amount = undefined
}

async function submit() {
  if (!form.orderId) { message.warning('请选择销售订单'); return }
  saving.value = true
  try {
    const payload: Record<string, unknown> = { orderId: form.orderId, taxRate: form.taxRate, remark: form.remark }
    if (form.amount) payload.amount = form.amount
    await invoiceApi.create(payload)
    message.success('发票草稿已创建')
    createOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

function openIssue(record: Invoice) {
  currentInvoice.value = record
  Object.keys(issueForm).forEach((key) => delete issueForm[key])
  issueForm.taxRate = record.taxRate
  issueOpen.value = true
}

async function submitIssue() {
  if (!currentInvoice.value) return
  if (!issueForm.externalInvoiceNo) { message.warning('请填写外部发票号'); return }
  saving.value = true
  try {
    await invoiceApi.issue(currentInvoice.value.id, { ...issueForm })
    message.success('发票已开具')
    issueOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function voidInvoice(record: Invoice) {
  try {
    await invoiceApi.void(record.id)
    message.success('发票已作废')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function redFlush(record: Invoice) {
  try {
    const red = await invoiceApi.redFlush(record.id)
    message.success(`已红冲，生成红字发票 ${red.invoiceNo}`)
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
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
