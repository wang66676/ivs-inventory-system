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
          <a-descriptions-item label="本机安装码">
            <a-space wrap>
              <code class="machine-code">{{ status.installationId }}</code>
              <a-button size="small" @click="copyMachineCode">复制</a-button>
            </a-space>
          </a-descriptions-item>
        </a-descriptions>
        <a-divider orientation="left">离线注册</a-divider>
        <a-form layout="vertical">
          <a-form-item label="注册码">
            <a-textarea v-model:value="activationCode" :rows="4" placeholder="粘贴作者生成的注册码" />
          </a-form-item>
          <a-button type="primary" :loading="activating" @click="activate">激活软件</a-button>
        </a-form>
        <a-alert type="warning" show-icon style="margin-top:14px"
                 :message="`试用期结束后如需继续使用，请联系作者：${status.contactEmail}`" />
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
const activating = ref(false)
const status = ref<LicenseStatus>()
const activationCode = ref('')

const stateLabel = computed(() => {
  if (status.value?.state === 'LICENSED') return '已注册'
  if (status.value?.state === 'EXPIRED') return '试用已到期'
  return '试用中'
})

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
    message.success('安装码已复制')
  } catch {
    message.warning('复制失败，请手动选择安装码')
  }
}

async function activate() {
  if (!activationCode.value.trim()) { message.warning('请粘贴注册码'); return }
  activating.value = true
  try {
    status.value = await licenseApi.activate(activationCode.value.trim())
    activationCode.value = ''
    message.success('软件注册成功')
    emit('activated', status.value)
  } catch (error) { message.error((error as Error).message) }
  finally { activating.value = false }
}

onMounted(reload)
defineExpose({ reload })
</script>

<style scoped>
.machine-code { display: inline-block; padding: 3px 7px; border-radius: 6px; background: #f3f6fa; word-break: break-all; }
</style>
