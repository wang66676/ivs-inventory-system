<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="全局参数仅系统管理员可查看和修改。安全参数、集成参数可能在新登录或服务重启后生效。" />
      <a-space style="margin-bottom:12px"><a-button @click="reload">刷新</a-button></a-space>
      <a-table :data-source="rows" :columns="columns" row-key="key" size="middle" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'value'"><code>{{ record.value }}</code></template>
          <template v-else-if="column.key === 'type'"><a-tag>{{ record.type }}</a-tag></template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatTime(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button size="small" type="primary" @click="openEdit(record)">修改</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
    <a-modal v-model:open="modalOpen" title="修改全局参数" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="参数"><a-input :value="current?.label" disabled /></a-form-item>
        <a-form-item label="参数键"><a-input :value="current?.key" disabled /></a-form-item>
        <a-form-item label="参数值" required><a-input v-model:value="value" /></a-form-item>
        <a-alert v-if="current?.description" type="info" show-icon :message="current.description" />
      </a-form>
    </a-modal>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { settingApi } from '../../api/setting'
import type { SystemSetting } from '../../types/setting'
const rows = ref<SystemSetting[]>([])
const modalOpen = ref(false); const saving = ref(false); const current = ref<SystemSetting>(); const value = ref('')
const columns = [
  { title: '分组', key: 'group', dataIndex: 'group', width: 120 },
  { title: '参数', key: 'label', dataIndex: 'label', width: 180 },
  { title: '参数键', key: 'key', dataIndex: 'key', width: 220 },
  { title: '值', key: 'value', width: 180 },
  { title: '类型', key: 'type', width: 100 },
  { title: '说明', key: 'description', dataIndex: 'description' },
  { title: '更新时间', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'actions', width: 90 },
]
function formatTime(v: string) { return v ? v.replace('T', ' ').slice(0, 19) : '-' }
function openEdit(record: SystemSetting) { current.value = record; value.value = record.value; modalOpen.value = true }
async function save() {
  if (!current.value) return
  saving.value = true
  try { await settingApi.update(current.value.key, value.value); message.success('全局参数已更新'); modalOpen.value = false; await reload() }
  catch (e) { message.error((e as Error).message) } finally { saving.value = false }
}
async function reload() { try { rows.value = await settingApi.list() } catch (e) { message.error((e as Error).message) } }
onMounted(reload)
</script>
