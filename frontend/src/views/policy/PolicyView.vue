<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="政策人工录入：必须绑定 OEM 厂家、填写有效期与限制维度。发布后订单可勾选；每条政策自动生成一个额度池。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">录入政策</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="状态"
                  style="width:150px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 12 }" :scroll="{ x: 1450 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">{{ POLICY_TYPE_LABELS[record.policyType] || record.policyType }}</template>
          <template v-else-if="column.key === 'benefit'">
            {{ record.benefitType === 'RATE' ? `${(Number(record.benefitValue) * 100).toFixed(2)}%` : money(record.benefitValue) }}
          </template>
          <template v-else-if="column.key === 'cycle'">{{ CYCLE_LABELS[record.claimCycle] || record.claimCycle }}</template>
          <template v-else-if="column.key === 'pool'">
            <div v-if="record.pool">
              <div>{{ record.pool.limitType === 'TIME_ONLY' ? '仅时间限制' : `${money(record.pool.remainingAmount)} / ${money(record.pool.totalAmount)}` }}</div>
              <a-progress v-if="record.pool.totalAmount" :percent="poolPercent(record)" size="small" />
              <div v-else class="sub">{{ LIMIT_TYPE_LABELS[record.pool.limitType] }}</div>
            </div>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button v-if="canApprove && record.status === 'DRAFT'" size="small" type="primary"
                        @click="publish(record)">发布</a-button>
              <a-button v-if="!canApprove && canWrite && record.status === 'DRAFT'" size="small" type="primary"
                        @click="submitPublish(record)">提交审批</a-button>
              <a-button v-if="canPool && record.pool && !record.pool.conditionMet && record.pool.conditionDesc"
                        size="small" @click="markCondition(record)">条件已达成</a-button>
              <a-button v-if="canApprove && record.status !== 'CANCELLED'" size="small" danger
                        @click="cancel(record)">作废</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="录入厂家政策" :confirm-loading="saving" ok-text="保存"
             cancel-text="取消" width="860px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="政策名目" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="政策类型" required>
              <a-select v-model:value="form.policyType" :options="policyTypeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="OEM 厂家" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search placeholder="选择整车厂" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="适用车型（可多选）">
              <a-select v-model:value="form.productIds" mode="multiple" :options="productOptions" show-search allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="计算方式" required>
              <a-select v-model:value="form.benefitType" :options="[{value:'AMOUNT',label:'定额'},{value:'RATE',label:'比例'}]" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item :label="form.benefitType === 'RATE' ? '比例（如 0.015）' : '单台金额'" required>
              <MoneyInput v-if="form.benefitType === 'AMOUNT'" v-model:value="form.benefitValue" :min="0" :precision="2" style="width:100%" />
              <a-input-number v-else v-model:value="form.benefitValue" :min="0" :precision="4" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="单台上限"><MoneyInput v-model:value="form.maxAmount" :min="0" :precision="2" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="生效日期" required><a-date-picker v-model:value="form.effFrom" format="YYYY-MM-DD" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="失效日期" required><a-date-picker v-model:value="form.effTo" format="YYYY-MM-DD" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8">
            <a-form-item label="参考时间" required>
              <a-select v-model:value="form.referenceDateType" :options="referenceOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="申报周期" required>
              <a-select v-model:value="form.claimCycle" :options="cycleOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="申报截止日（次期第 N 天）"><a-input-number v-model:value="form.claimCutoffDay" :min="0" :max="31" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="优先级"><a-input-number v-model:value="form.priority" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="限制维度" required>
              <a-select v-model:value="form.limitType" :options="limitOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="额度池总额">
              <MoneyInput v-model:value="form.totalAmount" :min="0" :precision="2" style="width:100%"
                              :disabled="form.limitType === 'TIME_ONLY' || form.limitType === 'CONDITION'" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="条件说明"><a-input v-model:value="form.conditionDesc" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="叠加规则">
              <a-select v-model:value="form.stackingRule" :options="[{value:'ADDITIVE',label:'累加'},{value:'MAX_ONE',label:'取最高'},{value:'EXCLUSIVE',label:'互斥'}]" />
            </a-form-item>
          </a-col>
          <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { policyApi } from '../../api/policy'
import { productApi, supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import {
  CYCLE_LABELS, LIMIT_TYPE_LABELS, POLICY_TYPE_LABELS,
} from '../../types/policy'
import type { Policy, PolicyStatus } from '../../types/policy'

const auth = useAuthStore()
const canWrite = auth.can('IVS_POLICY_WRITE')
const canApprove = auth.can('IVS_POLICY_APPROVE')
const canPool = auth.can('IVS_SUBSIDY_POOL_MANAGE')

const rows = ref<Policy[]>([])
const filterStatus = ref<PolicyStatus | undefined>()
const formOpen = ref(false)
const saving = ref(false)
const supplierOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const form = reactive<Record<string, any>>({})

const statusOptions = [
  { value: 'DRAFT', label: '草稿' }, { value: 'PUBLISHED', label: '已发布' },
  { value: 'CANCELLED', label: '已作废' }, { value: 'EXPIRED', label: '已过期' },
]
const policyTypeOptions = Object.entries(POLICY_TYPE_LABELS).map(([value, label]) => ({ value, label }))
const cycleOptions = Object.entries(CYCLE_LABELS).filter(([value]) => value !== 'MIXED').map(([value, label]) => ({ value, label }))
const referenceOptions = [
  { value: 'RETAIL_DATE', label: '实销日期（默认）' }, { value: 'ORDER_DATE', label: '采购订单时间（CG年月日）' },
  { value: 'INVOICE_DATE', label: '终端开票时间' }, { value: 'DELIVERY_DATE', label: '交付日期' },
  { value: 'REGISTER_DATE', label: '上牌日期' },
]
const limitOptions = Object.entries(LIMIT_TYPE_LABELS).map(([value, label]) => ({ value, label }))

const columns = [
  { title: '政策编号', key: 'policyNo', dataIndex: 'policyNo', width: 130 },
  { title: '政策名目', key: 'name', dataIndex: 'name', width: 220 },
  { title: '类型', key: 'type', width: 150 },
  { title: '金额/比例', key: 'benefit', width: 130 },
  { title: '有效期', key: 'effect', width: 200, customRender: ({ record }: any) => `${record.effFrom} ~ ${record.effTo}` },
  { title: '周期', key: 'cycle', width: 90 },
  { title: '额度池（剩余/总额）', key: 'pool', width: 230 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 230, fixed: 'right' },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function statusLabel(status: string) { return statusOptions.find((item) => item.value === status)?.label || status }
function statusColor(status: string) {
  if (status === 'PUBLISHED') return 'green'
  if (status === 'CANCELLED') return 'red'
  if (status === 'EXPIRED') return 'orange'
  return 'default'
}
function poolPercent(record: Policy) {
  const total = Number(record.pool?.totalAmount || 0)
  if (!total) return 0
  return Math.min(100, Math.round(Number(record.pool?.usedAmount || 0) / total * 100))
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, {
    policyType: 'PHASE_SUBSIDY', benefitType: 'AMOUNT', benefitValue: 0, effFrom: dayjs(),
    effTo: dayjs().add(1, 'month'), referenceDateType: 'RETAIL_DATE', claimCycle: 'MONTHLY',
    claimCutoffDay: 5, priority: 0, stackingRule: 'ADDITIVE', limitType: 'AMOUNT', totalAmount: 0,
  })
  formOpen.value = true
}

async function save() {
  if (!form.name || !form.supplierId) { message.warning('请填写政策名目并选择 OEM 厂家'); return }
  if (!form.effFrom || !form.effTo) { message.warning('请填写生效与失效日期'); return }
  saving.value = true
  try {
    await policyApi.create({
      ...form,
      effFrom: (form.effFrom as Dayjs).format('YYYY-MM-DD'),
      effTo: (form.effTo as Dayjs).format('YYYY-MM-DD'),
      productIds: form.productIds?.length ? form.productIds : null,
    })
    message.success('政策已保存，发布后可被订单选择')
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function publish(record: Policy) {
  try { await policyApi.publish(record.id); message.success('政策已发布'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function submitPublish(record: Policy) {
  try { await policyApi.submitPublish(record.id); message.success('政策已提交发布审批'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function cancel(record: Policy) {
  try { await policyApi.cancel(record.id); message.success('政策已作废'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function markCondition(record: Policy) {
  try { await policyApi.markConditionMet(record.id); message.success('条件已标记达成'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try { rows.value = await policyApi.list({ status: filterStatus.value }) }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [suppliers, products] = await Promise.all([supplierApi.list({ supplierType: 'OEM' }), productApi.list()])
    supplierOptions.value = suppliers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    productOptions.value = products.map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.sub { color: #8b96a8; font-size: 12px; }
</style>
