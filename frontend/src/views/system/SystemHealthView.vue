<template>
  <a-card :bordered="false">
    <a-space direction="vertical" :size="18" style="width:100%">
      <a-alert :type="alertType" show-icon :message="alertMessage" />
      <a-space wrap>
        <a-button @click="load" :loading="loading">刷新检查</a-button>
        <a-button type="primary" :loading="backingUp" @click="runBackup">立即备份并验证恢复</a-button>
        <a-tag :color="statusColor(health?.status)">{{ statusLabel(health?.status) }}</a-tag>
      </a-space>

      <a-descriptions bordered :column="3" size="small">
        <a-descriptions-item label="系统版本">{{ health?.version || '-' }}</a-descriptions-item>
        <a-descriptions-item label="检查时间">{{ formatTime(health?.checkedAt) }}</a-descriptions-item>
        <a-descriptions-item label="备份目录">{{ backupResult?.path || backupDir }}</a-descriptions-item>
        <a-descriptions-item label="最近备份">{{ latestBackup?.detail || '-' }}</a-descriptions-item>
        <a-descriptions-item label="恢复验证">{{ backupResult ? (backupResult.restoreVerified ? '通过' : '失败') : '-' }}</a-descriptions-item>
        <a-descriptions-item label="附件文件">{{ backupResult ? `${backupResult.attachmentFiles} 个` : '-' }}</a-descriptions-item>
      </a-descriptions>

      <a-table :data-source="health?.checks || []" :columns="columns" row-key="code" size="middle"
               :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
        </template>
      </a-table>

      <a-alert type="info" show-icon
               message="系统每天 02:30 自动执行在线备份；备份完成后会在临时数据库中执行一次恢复验证。发现异常时会向管理员发送站内告警。" />
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { systemHealthApi } from '../../api/systemHealth'
import type { BackupResult, SystemHealthStatus, SystemHealthView } from '../../types/systemHealth'

const loading = ref(false)
const backingUp = ref(false)
const health = ref<SystemHealthView>()
const backupResult = ref<BackupResult>()
const backupDir = ref('每日备份目录由系统配置决定')
const columns = [
  { title: '检查项', key: 'label', dataIndex: 'label', width: 180 },
  { title: '状态', key: 'status', width: 100 },
  { title: '说明', key: 'detail', dataIndex: 'detail' },
]
const latestBackup = computed(() => health.value?.checks.find((item) => item.code === 'BACKUP'))
const alertType = computed(() => health.value?.status === 'ERROR' ? 'error' : health.value?.status === 'WARN' ? 'warning' : 'success')
const alertMessage = computed(() => health.value?.status === 'ERROR'
  ? '系统存在需要立即处理的异常'
  : health.value?.status === 'WARN'
    ? '系统可运行，但存在需要处理的风险项'
    : '系统运行正常')

function statusLabel(status?: SystemHealthStatus) {
  return status === 'ERROR' ? '异常' : status === 'WARN' ? '警告' : status === 'OK' ? '正常' : '-'
}
function statusColor(status?: SystemHealthStatus) {
  return status === 'ERROR' ? 'red' : status === 'WARN' ? 'orange' : status === 'OK' ? 'green' : 'default'
}
function formatTime(value?: string) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-' }
async function load() {
  loading.value = true
  try { health.value = await systemHealthApi.status() }
  catch (error) { message.error((error as Error).message) }
  finally { loading.value = false }
}
async function runBackup() {
  backingUp.value = true
  try {
    backupResult.value = await systemHealthApi.backup()
    message.success(`备份完成并已通过恢复验证：${backupResult.value.fileName}`)
    await load()
  } catch (error) { message.error((error as Error).message) }
  finally { backingUp.value = false }
}
onMounted(load)
</script>