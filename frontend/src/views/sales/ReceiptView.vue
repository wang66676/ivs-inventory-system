<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="收款登记：按客户未结清应收的到期日 FIFO 自动核销；收款金额不能超过未收总额（避免预收挂账）。核销完成后若订单已交付且应收全清，订单自动关闭。" />
      <a-space style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">登记收款</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="{ pageSize: 15 }">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.allocations" row-key="receivableId" size="small" :pagination="false"
                   :locale="{ emptyText: '无核销明细' }">
            <a-table-column title="核销应收" data-index="receivableNo" :width="180" />
            <a-table-column title="核销金额" data-index="amount" :width="140" />
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'method'">{{ methodLabel(record.method) }}</template>
          <template v-else-if="column.key === 'salesOrderNo'">{{ salesOrderNos(record) }}</template>
          <template v-else-if="column.key === 'receivedAt'">{{ dayjs(record.receivedAt).format('YYYY-MM-DD') }}</template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" title="登记收款" :confirm-loading="saving" ok-text="提交并核销" cancel-text="取消"
             width="560px" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="客户" required><a-select v-model:value="form.customerId" :options="customerOptions" show-search /></a-form-item>
        <a-form-item label="收款金额" required><MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" style="width:100%" /></a-form-item>
        <a-form-item label="收款方式">
          <a-select v-model:value="form.method" :options="methodOptions" />
        </a-form-item>
        <a-form-item label="收款时间" required>
          <a-date-picker v-model:value="form.receivedAt" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" />
        </a-form-item>
        <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { salesApi } from '../../api/sales'
import { customerApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { PAYMENT_METHOD_LABELS } from '../../types/sales'
import type { PaymentMethod, Receipt } from '../../types/sales'

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')

const rows = ref<Receipt[]>([])
const modalOpen = ref(false)
const saving = ref(false)
const customerOptions = ref<{ label: string; value: string }[]>([])
const form = reactive<Record<string, any>>({ method: 'BANK_TRANSFER' })

const columns = [
  { title: '收款单号', key: 'receiptNo', dataIndex: 'receiptNo', width: 150 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 220 },
  { title: '销售订单编号', key: 'salesOrderNo', width: 190 },
  { title: '收款金额', key: 'amount', dataIndex: 'amount', width: 140 },
  { title: '方式', key: 'method', width: 120 },
  { title: '收款时间', key: 'receivedAt', width: 120 },
  { title: '备注', key: 'remark', dataIndex: 'remark' },
]

const methodOptions = Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => ({ value, label }))
const methodLabel = (method: string) => PAYMENT_METHOD_LABELS[method as PaymentMethod]
const salesOrderNos = (receipt: Receipt) => [...new Set(receipt.allocations.map((item) => item.orderNo).filter(Boolean))].join('、') || '-'

async function reload() {
  try {
    rows.value = await salesApi.receipts()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  form.method = 'BANK_TRANSFER'
  form.receivedAt = dayjs().format('YYYY-MM-DD')
  modalOpen.value = true
}

async function submit() {
  if (!form.customerId) { message.warning('请选择客户'); return }
  if (!form.amount || form.amount <= 0) { message.warning('请填写收款金额'); return }
  if (!form.receivedAt) { message.warning('请选择收款时间'); return }
  saving.value = true
  try {
    const receipt = await salesApi.createReceipt({ ...form })
    message.success(`收款成功，核销 ${receipt.allocations.length} 笔应收`)
    modalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const customers = await customerApi.list()
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>
