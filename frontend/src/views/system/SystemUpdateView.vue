<template>
  <div>
    <a-card :bordered="false">
      <template #title>
        <a-space><CloudDownloadOutlined /><span>在线检查更新</span></a-space>
      </template>
      <template #extra><a-button :loading="loading" @click="check">重新检查</a-button></template>

      <a-alert type="info" show-icon style="margin-bottom:16px"
               message="更新始终安装最新完整版本。即使跨越多个未更新版本，也会一次补齐所有中间版本的功能，无需逐版安装。" />

      <a-descriptions bordered :column="3" size="small" style="margin-bottom:16px">
        <a-descriptions-item label="当前版本">{{ currentVersion }}</a-descriptions-item>
        <a-descriptions-item label="最新版本">{{ latestVersion || '-' }}</a-descriptions-item>
        <a-descriptions-item label="检查状态">{{ checkMessage }}</a-descriptions-item>
      </a-descriptions>

      <a-spin :spinning="loading">
        <a-result v-if="error" status="warning" title="暂时无法检查更新" :sub-title="error">
          <template #extra>
            <a-space>
              <a-button type="primary" @click="check">重试</a-button>
              <a-button :href="releasePageUrl" target="_blank">打开 GitHub 发布页</a-button>
            </a-space>
          </template>
        </a-result>

        <template v-else-if="release">
          <a-alert :type="updateAvailable ? 'warning' : 'success'" show-icon style="margin-bottom:16px"
                   :message="updateAvailable ? `发现新版本 ${latestVersion}，可手动更新` : '当前已经是最新版本'" />
          <a-descriptions bordered :column="2" size="small" style="margin-bottom:16px">
            <a-descriptions-item label="发布日期">{{ releaseDate }}</a-descriptions-item>
            <a-descriptions-item label="版本说明">
              <div class="release-note">{{ release.body || '本次更新未提供详细说明' }}</div>
            </a-descriptions-item>
          </a-descriptions>

          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-card title="网页登录版" size="small" class="update-card">
                <p>网页版由服务器统一更新，刷新页面即可加载最新版本。</p>
                <a-button type="primary" block @click="reloadWeb">加载最新网页版</a-button>
              </a-card>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-card title="电脑安装包版" size="small" class="update-card">
                <p>下载最新完整安装包，覆盖安装后自动升级到最新版本。</p>
                <a-button type="primary" block :href="windowsAsset?.browser_download_url" target="_blank"
                          :disabled="!windowsAsset">下载 Windows 安装包</a-button>
              </a-card>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-card title="手机安卓 APK 版" size="small" class="update-card">
                <p>下载最新 APK 后覆盖安装，包含所有中间版本更新。</p>
                <a-button type="primary" block :href="androidAsset?.browser_download_url" target="_blank"
                          :disabled="!androidAsset">下载 Android APK</a-button>
              </a-card>
            </a-col>
          </a-row>

          <a-divider />
          <a-space>
            <a-button :href="release.html_url" target="_blank">查看完整 Release</a-button>
            <a-button :href="releasePageUrl" target="_blank">所有历史版本</a-button>
          </a-space>
        </template>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CloudDownloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

interface ReleaseAsset {
  name: string
  browser_download_url: string
}

interface GitHubRelease {
  tag_name: string
  name: string
  html_url: string
  body?: string
  published_at: string
  assets: ReleaseAsset[]
}

const owner = import.meta.env.VITE_GITHUB_OWNER || 'wang66676'
const repo = import.meta.env.VITE_GITHUB_REPO || 'ivs-inventory-system'
const currentVersion = import.meta.env.VITE_APP_VERSION || '1.0.0'
const releasePageUrl = `https://github.com/${owner}/${repo}/releases`
const loading = ref(false)
const error = ref('')
const release = ref<GitHubRelease>()

const latestVersion = computed(() => release.value?.tag_name?.replace(/^v/i, '') || '')
const updateAvailable = computed(() => compareVersion(latestVersion.value, currentVersion) > 0)
const checkMessage = computed(() => error.value ? '检查失败' : updateAvailable.value ? '有新版本' : '已是最新')
const releaseDate = computed(() => release.value?.published_at
  ? dayjs(release.value.published_at).format('YYYY-MM-DD HH:mm') : '-')
const windowsAsset = computed(() => release.value?.assets.find((asset) => /\.exe$/i.test(asset.name)))
const androidAsset = computed(() => release.value?.assets.find((asset) => /\.apk$/i.test(asset.name)))

async function check() {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch(`https://api.github.com/repos/${owner}/${repo}/releases/latest`, {
      headers: { Accept: 'application/vnd.github+json' },
    })
    if (!response.ok) {
      if (response.status === 404) throw new Error('仓库尚未发布正式 Release，或仓库不是公开仓库')
      if (response.status === 403) throw new Error('GitHub 接口访问频率受限，请稍后再试')
      throw new Error(`GitHub 返回状态 ${response.status}`)
    }
    release.value = await response.json() as GitHubRelease
  } catch (reason) {
    error.value = (reason as Error).message
  } finally {
    loading.value = false
  }
}

function compareVersion(left: string, right: string) {
  const normalize = (value: string) => value.replace(/^v/i, '').split(/[.-]/).map((part) => Number(part) || 0)
  const a = normalize(left)
  const b = normalize(right)
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    const difference = (a[index] || 0) - (b[index] || 0)
    if (difference !== 0) return difference
  }
  return 0
}

function reloadWeb() {
  window.location.reload()
}

onMounted(check)
</script>

<style scoped>
.update-card { height: 100%; }
.update-card :deep(.ant-card-body) { height: calc(100% - 48px); display: flex; flex-direction: column; }
.update-card :deep(.ant-card-body p) { flex: 1; color: #596579; }
.release-note { white-space: pre-wrap; max-height: 160px; overflow: auto; }
</style>
