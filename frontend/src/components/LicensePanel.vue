<template>
  <div class="license-panel">
    <a-spin :spinning="loading">
      <template v-if="status">
        <a-alert :type="status.state === 'EXPIRED' ? 'error' : status.state === 'LICENSED' ? 'success' : 'info'"
                 show-icon :message="status.message" style="margin-bottom:14px" />
        <a-descriptions bordered size="small" :column="1">
          <a-descriptions-item label="授权状态">
            <a-tag :color="status.state === 'LICENSED' ? 'green' : status.state === 'EXPIRED' ? 'red' : 'orange'">
              {{ stateLabel }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="剩余天数">{{ status.daysRemaining }} 天</a-descriptions-item>
          <a-descriptions-item label="试用开始">{{ formatTime(status.trialStartedAt) }}</a-descriptions-item>
          <a-descriptions-item label="试用到期">{{ formatTime(status.trialEndsAt) }}</a-descriptions-item>
          <a-descriptions-item v-if="status.licensedUntil" label="授权到期">{{ formatTime(status.licensedUntil) }}</a-descriptions-item>
          <a-descriptions-item v-if="status.customerName" label="授权客户">{{ status.customerName }}</a-descriptions-item>
          <a-descriptions-item v-if="status.username" label="授权用户名">{{ status.username }}</a-descriptions-item>
          <a-descriptions-item label="授权文件">
            <a-tag :color="fileColor">{{ fileStateLabel }}</a-tag>
            <span class="file-name">{{ status.licenseFileName }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="联系作者">{{ status.contactEmail }}</a-descriptions-item>
          <a-descriptions-item label="本机软件码">
            <a-space wrap>
              <code class="machine-code">{{ status.installationId }}</code>
              <a-button size="small" @click="copyMachineCode">复制</a-button>
            </a-space>
          </a-descriptions-item>
        </a-descriptions>
        <a-divider orientation="left">导入授权文件</a-divider>
        <a-alert type="info" show-icon style="margin-bottom:14px"
                 message="请联系作者购买或延期授权。把软件码、授权用户名和购买期限发送给作者，收到 ivs-license.lic 后在此导入，导入成功后重启软件生效。" />
        <a-space wrap>
          <a-upload :show-upload-list="false" accept=".lic,application/json" :before-upload="importFile" :disabled="importing">
            <a-button type="primary" :loading="importing">选择并导入 ivs-license.lic</a-button>
          </a-upload>
          <a-button @click="reload">重新检测授权文件</a-button>
        </a-space>
      </template>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { licenseApi } from '../api/license'
import type { LicenseStatus } from '../types/license'

const emit = defineEmits<{ activated: [status: LicenseStatus] }>()
const loading = ref(false)
const importing = ref(false)
const status = ref<LicenseStatus>()

const stateLabel = computed(() => {
  if (status.value?.state === 'LICENSED') return '已授权'
  if (status.value?.state === 'EXPIRED') return '试用已到期'
  return '试用中'
})
const fileStateLabel = computed(() => ({
  VALID: '有效', MISSING: '未导入', INVALID: '无效', EXPIRED: '已过期',
  NOT_YET_VALID: '尚未生效', MACHINE_MISMATCH: '软件码不匹配',
} as Record<string, string>)[status.value?.licenseFileState || 'MISSING'] || status.value?.licenseFileState || '-')
const fileColor = computed(() => status.value?.licenseFileState === 'VALID' ? 'green'
  : status.value?.licenseFileState === 'MISSING' ? 'default' : 'red')

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

async function reload() {
  loading.value = true
  try { status.value = await licenseApi.status() }
  catch (error) { message.error((error as Error).message) }
  finally { loading.value = false }
}

async function copyMachineCode() {
  if (!status.value) return
  try {
    await navigator.clipboard.writeText(status.value.installationId)
    message.success('软件码已复制')
  } catch {
    message.warning('复制失败，请手动选择软件码')
  }
}

async function importFile(file: File) {
  importing.value = true
  try {
    status.value = await licenseApi.importFile(file)
    message.success('授权文件导入成功，请重启软件后生效')
    emit('activated', status.value)
  } catch (error) { message.error((error as Error).message) }
  finally { importing.value = false }
  return false
}

onMounted(reload)
defineExpose({ reload })
</script>

<style scoped>
.machine-code { display: inline-block; padding: 3px 7px; border-radius: 6px; background: #f3f6fa; word-break: break-all; }
.file-name { margin-left: 8px; color: #6b7280; font-size: 12px; }
</style>
