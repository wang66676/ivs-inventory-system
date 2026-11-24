<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="其他用户删除自己登记的业务数据时必须提交申请。管理员核对业务关系后批准，系统会同步清理不再成立的下游数据；驳回时必须填写原因。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="status" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:170px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" row-key="id" size="middle" :pagination="{ pageSize: 20, showSizeChanger: true }"
               :scroll="{ x: 1180 }">
        <a-table-column title="数据类型" key="businessTypeLabel" data-index="businessTypeLabel" :width="140" />
        <a-table-column title="业务编号" key="businessNo" data-index="businessNo" :width="180" />
        <a-table-column title="当前模块" key="moduleName" data-index="moduleName" :width="150" />
        <a-table-column title="申请人" key="requesterName" data-index="requesterName" :width="120" />
        <a-table-column title="申请原因" key="reason" :width="230" ellipsis>
          <template #default="{ record }">{{ record.reason || '-' }}</template>
        </a-table-column>
        <a-table-column title="申请时间" key="requestedAt" :width="170">
          <template #default="{ record }">{{ formatTime(record.requestedAt) }}</template>
        </a-table-column>
        <a-table-column title="状态" key="status" :width="100">
          <template #default="{ record }">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="审批人 / 意见" key="review" :width="200">
          <template #default="{ record }">
            <div>{{ record.reviewerName || '-' }}</div>
            <div class="sub">{{ record.reviewRemark || '-' }}</div>
          </template>
        </a-table-column>
        <a-table-column title="操作" key="actions" :width="170" fixed="right">
          <template #default="{ record }">
            <a-space v-if="record.status === 'PENDING'">
              <a-button size="small" type="primary" @click="openReview(record, 'APPROVE')">批准删除</a-button>
              <a-button size="small" danger @click="openReview(record, 'REJECT')">驳回</a-button>
            </a-space>
            <span v-else>-</span>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-modal v-model:open="reviewOpen" :title="reviewMode === 'APPROVE' ? '批准删除申请' : '驳回删除申请'"
             :confirm-loading="saving" :ok-text="reviewMode === 'APPROVE' ? '确认批准并删除' : '确认驳回'"
             cancel-text="取消" @ok="submitReview">
      <a-alert v-if="reviewMode === 'APPROVE'" type="warning" show-icon style="margin-bottom:12px"
               :message="`将删除 ${current?.businessTypeLabel || ''}「${current?.businessNo || ''}」，并同步清理其不再成立的下游数据。`" />
      <a-form layout="vertical">
        <a-form-item :label="reviewMode === 'REJECT' ? '驳回原因（必填）' : '审批备注（选填）'">
          <a-textarea v-model:value="remark" :rows="4" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { deletionApi } from '../../api/deletion'
import { DELETION_STATUS_LABELS } from '../../types/deletion'
import type { DeletionRequest, DeletionRequestStatus } from '../../types/deletion'

const rows = ref<DeletionRequest[]>([])
const status = ref<DeletionRequestStatus>()
const reviewOpen = ref(false)
const reviewMode = ref<'APPROVE' | 'REJECT'>('APPROVE')
const current = ref<DeletionRequest>()
const remark = ref('')
const saving = ref(false)
const statusOptions = Object.entries(DELETION_STATUS_LABELS).map(([value, label]) => ({ value, label }))

function formatTime(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function statusColor(value: DeletionRequestStatus) { return value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : 'orange' }
function statusLabel(value: DeletionRequestStatus) { return DELETION_STATUS_LABELS[value] }

async function reload() {
  try { rows.value = await deletionApi.list(status.value) }
  catch (error) { message.error((error as Error).message) }
}

function openReview(record: DeletionRequest, mode: 'APPROVE' | 'REJECT') {
  current.value = record
  reviewMode.value = mode
  remark.value = ''
  reviewOpen.value = true
}

async function submitReview() {
  if (!current.value) return
  if (reviewMode.value === 'REJECT' && !remark.value.trim()) { message.warning('驳回时必须填写原因'); return }
  saving.value = true
  try {
    if (reviewMode.value === 'APPROVE') await deletionApi.approve(current.value.id, remark.value.trim() || undefined)
    else await deletionApi.reject(current.value.id, remark.value.trim())
    message.success(reviewMode.value === 'APPROVE' ? '已批准并完成删除' : '删除申请已驳回')
    reviewOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) }
  finally { saving.value = false }
}

onMounted(reload)
</script>

<style scoped>
.sub { margin-top: 3px; color: #8b96a8; font-size: 12px; }
</style>
