<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="10">
        <a-card title="迁移数据（JSON）" :bordered="false">
          <a-alert type="info" show-icon style="margin-bottom:12px"
                   message="先校验、再导入。迁移批次会记录每一条目标数据，可用于对账和回滚；已有后续业务引用的数据不允许回滚。" />
          <a-space wrap style="margin-bottom:10px">
            <a-button @click="downloadTemplate">下载模板</a-button>
            <a-button :loading="validating" @click="validate">试运行校验</a-button>
            <a-button type="primary" :loading="importing" @click="importData">正式导入</a-button>
          </a-space>
          <a-textarea v-model:value="jsonText" :rows="24" class="json-area" placeholder="粘贴迁移 JSON" />
          <a-alert v-if="validation" style="margin-top:12px" :type="validation.valid ? 'success' : 'error'" show-icon
                   :message="validation.valid ? '校验通过' : '校验失败'">
            <template #description>
              <div v-if="validation.valid">
                数据行：{{ countSummary }}；预计金额：{{ money(validation.totalAmount) }}
              </div>
              <ul v-else class="errors">
                <li v-for="error in validation.errors" :key="error">{{ error }}</li>
              </ul>
            </template>
          </a-alert>
        </a-card>
      </a-col>
      <a-col :span="14">
        <a-card title="迁移批次" :bordered="false">
          <a-space style="margin-bottom:12px"><a-button @click="reload">刷新</a-button></a-space>
          <a-table :data-source="batches" :columns="columns" row-key="id" size="small"
                   :pagination="{ pageSize: 10 }" :scroll="{ x: 900 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="record.status === 'IMPORTED' ? 'green' : 'default'">
                  {{ record.status === 'IMPORTED' ? '已导入' : '已回滚' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'amount'">{{ money(record.totalAmount) }}</template>
              <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
              <template v-else-if="column.key === 'actions'">
                <a-space>
                  <a-button size="small" @click="reconcile(record)">对账</a-button>
                  <a-button v-if="record.status === 'IMPORTED'" size="small" danger @click="rollback(record)">回滚</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <a-modal v-model:open="reconcileOpen" title="迁移对账结果" footer="" width="720px">
      <a-result v-if="reconciliation" :status="reconciliation.ok ? 'success' : 'warning'"
                :title="reconciliation.ok ? '对账一致' : '发现缺失或异常'"
                :sub-title="`批次 ${reconciliation.batchNo} · ${reconciliation.itemCount} 条明细`">
        <template #extra>
          <a-space direction="vertical" style="text-align:left;width:100%">
            <div v-for="message in reconciliation.messages" :key="message">{{ message }}</div>
            <div v-if="reconciliation.missingTargets.length" class="errors">
              缺失目标：{{ reconciliation.missingTargets.join('；') }}
            </div>
          </a-space>
        </template>
      </a-result>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { migrationApi } from '../../api/migration'
import type { MigrationBatch, MigrationReconciliation, MigrationValidation } from '../../types/migration'

const jsonText = ref(JSON.stringify(template(), null, 2))
const validation = ref<MigrationValidation>()
const batches = ref<MigrationBatch[]>([])
const validating = ref(false)
const importing = ref(false)
const reconcileOpen = ref(false)
const reconciliation = ref<MigrationReconciliation>()
const columns = [
  { title: '批次号', key: 'batchNo', dataIndex: 'batchNo', width: 130 },
  { title: '名称', key: 'name', dataIndex: 'name', width: 170 },
  { title: '来源系统', key: 'sourceSystem', dataIndex: 'sourceSystem', width: 120 },
  { title: '明细数', key: 'itemCount', dataIndex: 'itemCount', width: 80 },
  { title: '金额', key: 'amount', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '导入时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 150 },
]

const countSummary = computed(() => validation.value
  ? Object.entries(validation.value.counts).map(([key, value]) => `${key}=${value}`).join('，')
  : '')

function parsePayload() {
  try { return JSON.parse(jsonText.value) } catch (error) { throw new Error('JSON 格式错误：' + (error as Error).message) }
}
function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }

async function validate() {
  validating.value = true
  try {
    validation.value = await migrationApi.validate(parsePayload())
    if (!validation.value.valid) message.warning('校验未通过，请修正错误')
  } catch (error) { message.error((error as Error).message) } finally { validating.value = false }
}

async function importData() {
  Modal.confirm({
    title: '确认正式导入？',
    content: '导入会写入正式业务数据；建议先通过试运行校验。',
    okText: '确认导入', cancelText: '取消',
    onOk: async () => {
      importing.value = true
      try {
        const batch = await migrationApi.import(parsePayload())
        message.success(`迁移批次 ${batch.batchNo} 导入成功，共 ${batch.itemCount} 条`)
        validation.value = undefined
        await reload()
      } catch (error) { message.error((error as Error).message) } finally { importing.value = false }
    },
  })
}

async function reconcile(record: MigrationBatch) {
  try {
    reconciliation.value = await migrationApi.reconcile(record.id)
    reconcileOpen.value = true
  } catch (error) { message.error((error as Error).message) }
}

function rollback(record: MigrationBatch) {
  Modal.confirm({
    title: `确认回滚 ${record.batchNo}？`,
    content: '将删除该批次导入的数据；如已有后续业务引用，系统会拒绝回滚。',
    okText: '确认回滚', cancelText: '取消',
    onOk: async () => {
      try { await migrationApi.rollback(record.id); message.success('迁移批次已回滚'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

async function reload() {
  try { batches.value = await migrationApi.list() }
  catch (error) { message.error((error as Error).message) }
}

function template() {
  return {
    name: '上线历史数据', sourceSystem: '旧ERP', remark: '',
    customers: [], suppliers: [], categories: [], products: [], serials: [], openingStocks: [],
    receivables: [], fundOpenings: [], payables: [],
  }
}

function downloadTemplate() {
  const blob = new Blob([JSON.stringify(template(), null, 2)], { type: 'application/json;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = 'ivs-migration-template.json'
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(reload)
</script>

<style scoped>
.json-area { font-family: Consolas, monospace; font-size: 12px; }
.errors { margin: 8px 0 0; padding-left: 18px; color: #cf1322; }
</style>

