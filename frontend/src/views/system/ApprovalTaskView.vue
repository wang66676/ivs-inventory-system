<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:14px"
               message="只显示当前账号有权审批的待办及本人已处理记录。通过或驳回后，申请人和相关权限用户会收到站内通知。" />
      <a-tabs v-model:activeKey="activeStatus" @change="reload">
        <a-tab-pane key="PENDING" tab="待我审批" />
        <a-tab-pane key="APPROVED" tab="已通过" />
        <a-tab-pane key="REJECTED" tab="已驳回" />
        <a-tab-pane key="CANCELLED" tab="已取消" />
      </a-tabs>
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-tag color="orange">待处理 {{ pendingCount }}</a-tag>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :loading="loading" :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 1350 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'amount'">{{ record.amount == null ? '-' : money(record.amount) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDate(record.createdAt) }}</template>
          <template v-else-if="column.key === 'decidedAt'">{{ formatDate(record.decidedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="record.canDecide">
              <a-button size="small" type="primary" @click="approve(record)">通过</a-button>
              <a-button size="small" danger @click="openReject(record)">驳回</a-button>
            </a-space>
            <span v-else-if="record.decisionByName">{{ record.decisionByName }}</span>
            <span v-else>-</span>
          </template>
        </template>
        <template #expandedRowRender="{ record }">
          <div class="task-detail">
            <div><strong>任务说明：</strong>{{ record.summary || '-' }}</div>
            <div><strong>审批权限：</strong><code>{{ record.approvalPermission }}</code></div>
            <div v-if="record.decisionReason"><strong>处理意见：</strong>{{ record.decisionReason }}</div>
            <a-button type="link" style="padding-left:0" @click="goBusiness(record)">查看关联业务</a-button>
          </div>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="rejectOpen" title="驳回审批" :confirm-loading="saving" ok-text="确认驳回"
             cancel-text="取消" ok-type="danger" @ok="reject">
      <a-form layout="vertical">
        <a-form-item label="审批单"><a-input :value="current?.title" disabled /></a-form-item>
        <a-form-item label="驳回原因" required>
          <a-textarea v-model:value="rejectReason" :rows="4" :maxlength="500" show-count placeholder="请说明需要申请人修改的内容" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { approvalApi } from '../../api/approval'
import { salesApi } from '../../api/sales'
import { approvalBusinessLocation } from '../../utils/approvalNavigation'
import type { ApprovalTask, ApprovalTaskStatus } from '../../types/approval'

const router = useRouter()
const activeStatus = ref<ApprovalTaskStatus>('PENDING')
const rows = ref<ApprovalTask[]>([])
const loading = ref(false)
const saving = ref(false)
const rejectOpen = ref(false)
const current = ref<ApprovalTask>()
const rejectReason = ref('')
const pendingCount = computed(() => activeStatus.value === 'PENDING' ? rows.value.length : undefined)
const columns = [
  { title: '审批单号', key: 'taskNo', dataIndex: 'taskNo', width: 130 },
  { title: '审批事项', key: 'title', dataIndex: 'title', width: 240 },
  { title: '关联单号', key: 'businessNo', dataIndex: 'businessNo', width: 150 },
  { title: '申请人', key: 'applicantName', dataIndex: 'applicantName', width: 110 },
  { title: '金额', key: 'amount', width: 130, align: 'right' },
  { title: '状态', key: 'status', width: 100 },
  { title: '提交时间', key: 'createdAt', width: 120 },
  { title: '处理时间', key: 'decidedAt', width: 120 },
  { title: '处理人/操作', key: 'actions', width: 150, fixed: 'right' },
]
function statusLabel(value: ApprovalTaskStatus) {
  return ({ PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消' })[value]
}
function statusColor(value: ApprovalTaskStatus) {
  return ({ PENDING: 'orange', APPROVED: 'green', REJECTED: 'red', CANCELLED: 'default' })[value]
}
function money(value: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function goBusiness(task: ApprovalTask) {
  const location = approvalBusinessLocation(task)
  if (location) router.push(location)
  else message.warning(`未识别的审批业务类型：${task.businessType}`)
}
async function approve(task: ApprovalTask) {
  saving.value = true
  try {
    if (task.taskType === 'SALES_PRICE') await salesApi.approvePrice(task.businessId)
    else if (task.taskType === 'STOCKTAKE_POST') await approvalApi.approveTask(task.id)
    else if (task.taskType === 'POLICY_PUBLISH') await approvalApi.approveTask(task.id)
    else if (task.taskType === 'SUBSIDY_POOL_ADJUSTMENT') await approvalApi.approveTask(task.id)
    else if (task.taskType === 'FUND_POOL_CREDIT') await approvalApi.approveTask(task.id)
    else if (task.taskType === 'REFUND_APPROVAL') await approvalApi.approveTask(task.id)
    else { message.warning('该类型审批将在对应业务模块中处理'); return }
    message.success('审批已通过')
    await reload()
  } catch (error) { message.error((error as Error).message) }
  finally { saving.value = false }
}
function openReject(task: ApprovalTask) {
  current.value = task
  rejectReason.value = ''
  rejectOpen.value = true
}
async function reject() {
  if (!current.value) return
  if (!rejectReason.value.trim()) { message.warning('请填写驳回原因'); return }
  saving.value = true
  try {
    if (current.value.taskType === 'SALES_PRICE') {
      await salesApi.rejectPrice(current.value.businessId, rejectReason.value.trim())
    } else if (current.value.taskType === 'STOCKTAKE_POST') {
      await approvalApi.rejectTask(current.value.id, rejectReason.value.trim())
    } else if (current.value.taskType === 'POLICY_PUBLISH') {
      await approvalApi.rejectTask(current.value.id, rejectReason.value.trim())
    } else if (current.value.taskType === 'SUBSIDY_POOL_ADJUSTMENT'
      || current.value.taskType === 'FUND_POOL_CREDIT') {
      await approvalApi.rejectTask(current.value.id, rejectReason.value.trim())
    } else if (current.value.taskType === 'REFUND_APPROVAL') {
      await approvalApi.rejectTask(current.value.id, rejectReason.value.trim())
    } else { message.warning('该类型审批将在对应业务模块中处理'); return }
    message.success('审批已驳回')
    rejectOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) }
  finally { saving.value = false }
}
async function reload() {
  loading.value = true
  try { rows.value = await approvalApi.tasks(activeStatus.value) }
  catch (error) { message.error((error as Error).message) }
  finally { loading.value = false }
}
onMounted(reload)
</script>

<style scoped>
.task-detail { display: grid; gap: 8px; color: #46536a; }
</style>
