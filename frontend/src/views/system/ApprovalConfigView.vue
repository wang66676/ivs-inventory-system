<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="warning" show-icon style="margin-bottom:14px"
               message="审批规则仅系统管理员可查看和修改。规则保存后立即生效；关闭规则表示该业务不再触发审批门禁。" />
      <a-row :gutter="12" style="margin-bottom:14px">
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="审批规则" :value="rows.length" suffix="项" /></a-card></a-col>
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="已启用" :value="enabledCount" suffix="项" /></a-card></a-col>
        <a-col :xs="24" :md="12"><a-card size="small"><a-statistic title="最近更新" :value="latestUpdate" /></a-card></a-col>
      </a-row>
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-tag color="blue">低价规则已接入销售订单</a-tag>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="ruleCode" size="middle"
               :pagination="false" :scroll="{ x: 1250 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'scenario'">{{ scenarioLabel(record.scenario) }}</template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '已启用' : '已关闭' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'condition'">{{ conditionLabel(record) }}</template>
          <template v-else-if="column.key === 'approvalPermission'"><code>{{ record.approvalPermission }}</code></template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatDate(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button size="small" type="primary" @click="openEdit(record)">配置</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" title="配置审批规则" :confirm-loading="saving" ok-text="保存并生效"
             cancel-text="取消" width="620px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="16"><a-form-item label="规则"><a-input :value="current?.ruleName" disabled /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="状态"><a-switch v-model:checked="form.enabled" checked-children="启用" un-checked-children="关闭" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="触发方式">
          <a-select v-model:value="form.metricType" :options="metricOptions" />
        </a-form-item>
        <a-form-item v-if="form.metricType === 'AMOUNT'" label="金额阈值（元）">
          <a-input-number v-model:value="form.thresholdValue" :min="0" :precision="2" style="width:100%" />
        </a-form-item>
        <a-form-item v-else-if="form.metricType === 'DISCOUNT_PERCENT'" label="折让比例阈值（%）">
          <a-input-number v-model:value="form.thresholdValue" :min="0" :max="100" :precision="2" style="width:100%" />
          <div class="field-hint">例如填 5：低于最低限价达到 5% 才触发审批；填 0 表示任何低价都触发。</div>
        </a-form-item>
        <a-form-item label="审批权限">
          <a-select v-model:value="form.approvalPermission" :options="permissionOptions" show-search />
        </a-form-item>
        <a-form-item label="规则说明"><a-textarea v-model:value="form.description" :rows="3" :maxlength="500" show-count /></a-form-item>
        <a-alert type="info" show-icon message="影响范围：保存后新提交的业务立即按新阈值判断，已进入审批或已完成的单据不会被追溯修改。" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { approvalApi } from '../../api/approval'
import type { ApprovalMetricType, ApprovalRule } from '../../types/approval'

const rows = ref<ApprovalRule[]>([])
const modalOpen = ref(false)
const saving = ref(false)
const current = ref<ApprovalRule>()
const form = reactive({
  enabled: true,
  metricType: 'AMOUNT' as ApprovalMetricType,
  thresholdValue: 0,
  approvalPermission: '',
  description: '',
})
const columns = [
  { title: '业务范围', key: 'scenario', width: 110 },
  { title: '审批规则', key: 'ruleName', dataIndex: 'ruleName', width: 190 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '触发条件', key: 'condition', width: 240 },
  { title: '审批权限', key: 'approvalPermission', width: 220 },
  { title: '规则说明', key: 'description', dataIndex: 'description' },
  { title: '更新时间', key: 'updatedAt', width: 120 },
  { title: '操作', key: 'actions', width: 90, fixed: 'right' },
]
const metricOptions = [
  { value: 'ALWAYS', label: '始终需要审批' },
  { value: 'NONE', label: '无需审批' },
  { value: 'AMOUNT', label: '金额达到阈值' },
  { value: 'DISCOUNT_PERCENT', label: '折让比例达到阈值' },
]
const permissionOptions = [
  { value: 'IVS_PRICE_APPROVE', label: '价格审批（IVS_PRICE_APPROVE）' },
  { value: 'IVS_STOCK_ADJUST', label: '库存调整（IVS_STOCK_ADJUST）' },
  { value: 'IVS_POLICY_APPROVE', label: '政策审批（IVS_POLICY_APPROVE）' },
  { value: 'IVS_SUBSIDY_POOL_MANAGE', label: '额度池管理（IVS_SUBSIDY_POOL_MANAGE）' },
  { value: 'IVS_FUND_POOL_ADJUST', label: '资金池调整（IVS_FUND_POOL_ADJUST）' },
  { value: 'USER_MANAGE', label: '系统管理员（USER_MANAGE）' },
]
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length)
const latestUpdate = computed(() => {
  const latest = rows.value.map((row) => row.updatedAt).filter(Boolean).sort().at(-1)
  return latest ? formatDate(latest) : '-'
})
function scenarioLabel(value: string) {
  return ({ SALES: '销售', INVENTORY: '库存', POLICY: '政策', FUND: '资金' } as Record<string, string>)[value] || value
}
function conditionLabel(rule: ApprovalRule) {
  if (rule.metricType === 'ALWAYS') return '始终需要审批'
  if (rule.metricType === 'NONE') return '无需审批'
  if (rule.metricType === 'AMOUNT') return `金额 ≥ ${money(rule.thresholdValue)}`
  if (rule.metricType === 'DISCOUNT_PERCENT') return rule.thresholdValue === 0 ? '任何低价均需审批' : `折让比例 ≥ ${rule.thresholdValue}%`
  return rule.metricLabel
}
function money(value: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function openEdit(rule: ApprovalRule) {
  current.value = rule
  Object.assign(form, {
    enabled: rule.enabled,
    metricType: rule.metricType,
    thresholdValue: Number(rule.thresholdValue || 0),
    approvalPermission: rule.approvalPermission,
    description: rule.description || '',
  })
  modalOpen.value = true
}
async function save() {
  if (!current.value) return
  if (!form.approvalPermission) { message.warning('请选择审批权限'); return }
  saving.value = true
  try {
    await approvalApi.update(current.value.ruleCode, { ...form })
    message.success('审批规则已保存并立即生效')
    modalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}
async function reload() {
  try { rows.value = await approvalApi.list() }
  catch (error) { message.error((error as Error).message) }
}
onMounted(reload)
</script>

<style scoped>
.field-hint { margin-top: 6px; color: #7a8599; font-size: 12px; }
</style>
