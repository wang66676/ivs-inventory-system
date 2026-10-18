<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="政策数" :value="rows.length" /><div class="hint">每条政策一个额度池</div></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="金额池总额" :value="totalAmount" :precision="2" prefix="¥" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="已预占 / 使用" :value="usedAmount" :precision="2" prefix="¥" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="剩余额度" :value="remainingAmount" :precision="2" prefix="¥" /></a-card></a-col>
    </a-row>

    <a-card :bordered="false" style="margin-top:16px">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="额度池在订单勾选政策时原子预占，取消预享受自动释放；金额池返完为止，剩余低于 10% 时应在报表中重点预警。" />
      <a-space style="margin-bottom:12px"><a-button @click="reload">刷新</a-button></a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="{ pageSize: 15 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'limit'">{{ LIMIT_TYPE_LABELS[record.pool?.limitType] || '-' }}</template>
          <template v-else-if="column.key === 'progress'">
            <a-progress v-if="record.pool?.totalAmount" :percent="percent(record)" :status="percent(record) >= 90 ? 'exception' : 'normal'" />
            <span v-else class="sub">不扣金额</span>
          </template>
          <template v-else-if="column.key === 'condition'">
            <a-tag v-if="record.pool?.conditionMet" color="green">已达成</a-tag>
            <span v-else>{{ record.pool?.conditionDesc || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button v-if="canManage && record.pool && !record.pool.conditionMet && record.pool.conditionDesc"
                      size="small" @click="markCondition(record)">标记条件达成</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { useAuthStore } from '../../stores/auth'
import { LIMIT_TYPE_LABELS } from '../../types/policy'
import type { Policy } from '../../types/policy'

const auth = useAuthStore()
const canManage = auth.can('IVS_SUBSIDY_POOL_MANAGE')
const rows = ref<Policy[]>([])

const totalAmount = computed(() => rows.value.reduce((sum, row) => sum + Number(row.pool?.totalAmount || 0), 0))
const usedAmount = computed(() => rows.value.reduce((sum, row) => sum + Number(row.pool?.usedAmount || 0), 0))
const remainingAmount = computed(() => rows.value.reduce((sum, row) => sum + Number(row.pool?.remainingAmount || 0), 0))

const columns = [
  { title: '政策编号', key: 'policyNo', dataIndex: 'policyNo', width: 130 },
  { title: '政策名目', key: 'name', dataIndex: 'name', width: 240 },
  { title: '限制维度', key: 'limit', width: 160 },
  { title: '总额', key: 'total', width: 140, customRender: ({ record }: any) => money(record.pool?.totalAmount) },
  { title: '已用', key: 'used', width: 140, customRender: ({ record }: any) => money(record.pool?.usedAmount) },
  { title: '剩余', key: 'remaining', width: 140, customRender: ({ record }: any) => money(record.pool?.remainingAmount) },
  { title: '使用进度', key: 'progress', width: 220 },
  { title: '条件', key: 'condition', width: 220 },
  { title: '状态', key: 'status', width: 100, customRender: ({ record }: any) => record.pool?.status || '-' },
  { title: '操作', key: 'actions', width: 150 },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function percent(record: Policy) {
  const total = Number(record.pool?.totalAmount || 0)
  return total ? Math.min(100, Math.round(Number(record.pool?.usedAmount || 0) / total * 100)) : 0
}

async function markCondition(record: Policy) {
  try { await policyApi.markConditionMet(record.id); message.success('条件已标记达成'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try { rows.value = await policyApi.list() }
  catch (error) { message.error((error as Error).message) }
}

onMounted(reload)
</script>

<style scoped>
.hint { margin-top: 4px; color: #8b96a8; font-size: 12px; }
.sub { color: #8b96a8; }
</style>
