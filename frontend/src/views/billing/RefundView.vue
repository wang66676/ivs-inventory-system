<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="退款申请需审批后才能执行；执行失败可重试，系统保留失败原因和重试次数，也支持人工确认完成。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">发起退款</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1650 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="REFUND_STATUS_COLORS[record.status as RefundStatus]">{{ REFUND_STATUS_LABELS[record.status as RefundStatus] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'source'">{{ record.sourceNo || sourceLabel(record.sourceType) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDate(record.createdAt) }}</template>
          <template v-else-if="column.key === 'reason'">
            <div>{{ record.reason }}</div>
            <div v-if="record.failureReason" class="failure">失败：{{ record.failureReason }}</div>
            <div v-if="record.manualResolved" class="manual">人工兜底完成</div>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space wrap>
              <a-button v-if="canManage && ['APPROVED','FAILED'].includes(record.status)" size="small"
                        type="primary" @click="execute(record, true)">{{ record.status === 'FAILED' ? '重试' : '执行成功' }}</a-button>
              <a-button v-if="canManage && record.status === 'APPROVED'" size="small" danger
                        @click="failRefund(record)">模拟失败</a-button>
              <a-button v-if="canManage && ['APPROVED','FAILED'].includes(record.status)" size="small"
                        @click="manualComplete(record)">人工兜底</a-button>
              <a-button v-if="canWrite && ['PENDING_APPROVAL','APPROVED'].includes(record.status)" size="small"
                        danger @click="cancel(record)">取消</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="发起客户退款" :confirm-loading="saving" ok-text="提交退款申请"
             cancel-text="取消" width="680px" @ok="create">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="客户" required><a-select v-model:value="form.customerId" :options="customerOptions" show-search /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="退款金额" required><MoneyInput v-model:value="form.amount" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="退款方式"><a-select v-model:value="form.method" :options="methodOptions" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="来源类型"><a-select v-model:value="form.sourceType" :options="sourceOptions" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="来源单号"><a-input v-model:value="form.sourceNo" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="退款原因" required><a-textarea v-model:value="form.reason" :rows="3" :maxlength="500" show-count /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="failureOpen" title="登记退款执行失败" :confirm-loading="saving" ok-text="确认失败"
             cancel-text="取消" @ok="submitFailure">
      <a-form layout="vertical">
        <a-form-item label="退款单"><a-input :value="current?.refundNo" disabled /></a-form-item>
        <a-form-item label="失败原因" required><a-textarea v-model:value="failureReason" :rows="3" :maxlength="500" show-count /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { refundApi } from '../../api/refund'
import { customerApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { REFUND_STATUS_COLORS, REFUND_STATUS_LABELS } from '../../types/refund'
import type { RefundOrder, RefundStatus } from '../../types/refund'

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')
const canManage = auth.can('IVS_REFUND_MANAGE')
const rows = ref<RefundOrder[]>([])
const customerOptions = ref<{ label: string; value: string }[]>([])
const formOpen = ref(false)
const failureOpen = ref(false)
const saving = ref(false)
const current = ref<RefundOrder>()
const failureReason = ref('')
const form = reactive<Record<string, any>>({})
const methodOptions = [
  { value: 'BANK_TRANSFER', label: '银行转账' }, { value: 'CASH', label: '现金' },
  { value: 'OTHER', label: '其他' },
]
const sourceOptions = [
  { value: 'SALES_RETURN', label: '销售退货' }, { value: 'MANUAL', label: '手工退款' },
  { value: 'OTHER', label: '其他' },
]
const columns = [
  { title: '退款单号', key: 'refundNo', dataIndex: 'refundNo', width: 140 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 190 },
  { title: '退款金额', key: 'amount', width: 140, align: 'right' },
  { title: '方式', key: 'method', dataIndex: 'method', width: 120 },
  { title: '来源', key: 'source', width: 150 },
  { title: '原因/失败信息', key: 'reason', width: 300 },
  { title: '状态', key: 'status', width: 110 },
  { title: '重试次数', key: 'retryCount', dataIndex: 'retryCount', width: 100, align: 'center' },
  { title: '申请时间', key: 'createdAt', width: 120 },
  { title: '操作', key: 'actions', width: 310, fixed: 'right' },
]
function money(value: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function sourceLabel(value: string) { return sourceOptions.find((item) => item.value === value)?.label || value }
function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { amount: 0, method: 'BANK_TRANSFER', sourceType: 'MANUAL' })
  formOpen.value = true
}
async function create() {
  if (!form.customerId || !form.amount || Number(form.amount) <= 0 || !form.reason?.trim()) {
    message.warning('请填写客户、退款金额和原因'); return
  }
  saving.value = true
  try {
    await refundApi.create({ ...form })
    message.success('退款申请已提交，审批通过后可执行')
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
async function execute(record: RefundOrder, success: boolean) {
  saving.value = true
  try {
    await refundApi.execute(record.id, { success, remark: success ? '退款执行成功' : undefined })
    message.success(record.status === 'FAILED' ? '退款重试成功' : '退款执行成功')
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
function failRefund(record: RefundOrder) {
  current.value = record
  failureReason.value = ''
  failureOpen.value = true
}
async function submitFailure() {
  if (!current.value || !failureReason.value.trim()) { message.warning('请填写失败原因'); return }
  saving.value = true
  try {
    await refundApi.execute(current.value.id, { success: false, failureReason: failureReason.value.trim() })
    message.warning('退款执行失败已登记，可重试或人工兜底')
    failureOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
async function manualComplete(record: RefundOrder) {
  try {
    await refundApi.manualComplete(record.id, '人工确认退款完成')
    message.success('退款已人工确认完成')
    await reload()
  } catch (error) { message.error((error as Error).message) }
}
async function cancel(record: RefundOrder) {
  try { await refundApi.cancel(record.id); message.success('退款申请已取消'); await reload() }
  catch (error) { message.error((error as Error).message) }
}
async function reload() {
  try { rows.value = await refundApi.list() }
  catch (error) { message.error((error as Error).message) }
}
onMounted(async () => {
  try {
    const customers = await customerApi.list()
    customerOptions.value = customers.map((item) => ({ label: item.name, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.failure { margin-top: 4px; color: #cf1322; font-size: 12px; }
.manual { margin-top: 4px; color: #d48806; font-size: 12px; }
</style>
