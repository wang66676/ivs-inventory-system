<template>
  <div>
    <a-card :bordered="false">
      <a-alert v-if="description" type="info" show-icon :message="description" style="margin-bottom:12px" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新增{{ title }}</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="keyword" placeholder="搜索" allow-clear style="width:220px" />
        <span class="hint">共 {{ filtered.length }} 条</span>
      </a-space>
      <a-table :data-source="filtered" :columns="tableColumns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 900 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">
              {{ record.status === 'ACTIVE' ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a v-if="canWrite" @click="openEdit(record)">编辑</a>
              <a v-if="canWrite && toggle && record.status === 'ACTIVE'" @click="toggleRow(record, false)">停用</a>
              <a v-else-if="canWrite && toggle" @click="toggleRow(record, true)">启用</a>
            </a-space>
          </template>
          <template v-else>{{ display(column, record) }}</template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="(editing ? '编辑' : '新增') + title" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="680px" @ok="submit">
      <a-form layout="vertical">
        <a-form-item v-for="field in fields" :key="field.key" :label="field.label" :required="field.required">
          <a-select v-if="field.type === 'select'" v-model:value="form[field.key]" :options="field.options"
                    allow-clear :placeholder="field.placeholder" />
          <a-textarea v-else-if="field.type === 'textarea'" v-model:value="form[field.key]" :rows="3"
                      :placeholder="field.placeholder" />
          <MoneyInput v-else-if="field.type === 'money'" v-model:value="form[field.key]"
                      :min="0" :precision="2" style="width:100%" :placeholder="field.placeholder" />
          <a-input-number v-else-if="field.type === 'number'" v-model:value="form[field.key]"
                          style="width:100%" :placeholder="field.placeholder" />
          <a-input v-else v-model:value="form[field.key]" :placeholder="field.placeholder" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'

export interface CrudField {
  key: string
  label: string
  type?: 'text' | 'number' | 'money' | 'select' | 'textarea'
  required?: boolean
  options?: { label: string; value: string }[]
  placeholder?: string
}

export interface CrudColumn {
  key: string
  title: string
  width?: number
  render?: (row: Record<string, unknown>) => string
}

const props = defineProps<{
  title: string
  description?: string
  canWrite: boolean
  columns: CrudColumn[]
  fields: CrudField[]
  load: () => Promise<Record<string, unknown>[]>
  create?: (values: Record<string, unknown>) => Promise<unknown>
  update?: (id: string, values: Record<string, unknown>) => Promise<unknown>
  toggle?: (row: Record<string, unknown>, enable: boolean) => Promise<unknown>
}>()

const rows = ref<Record<string, unknown>[]>([])
const keyword = ref('')
const modalOpen = ref(false)
const saving = ref(false)
const editing = ref<Record<string, unknown> | null>(null)
const form = reactive<Record<string, unknown>>({})

const tableColumns = computed(() => [
  ...props.columns.map((column) => ({ title: column.title, key: column.key, dataIndex: column.key, width: column.width })),
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 140 },
])

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((row) => Object.values(row).some((value) =>
    value != null && String(value).toLowerCase().includes(kw)))
})

function display(column: { key: string }, record: Record<string, unknown>) {
  const config = props.columns.find((item) => item.key === column.key)
  if (config?.render) return config.render(record)
  const value = record[column.key]
  return value == null || value === '' ? '-' : String(value)
}

async function reload() {
  try {
    rows.value = await props.load()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  props.fields.forEach((field) => {
    form[field.key] = field.type === 'number' ? undefined : undefined
  })
}

function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

function openEdit(record: Record<string, unknown>) {
  editing.value = record
  resetForm()
  props.fields.forEach((field) => {
    form[field.key] = record[field.key]
  })
  modalOpen.value = true
}

async function submit() {
  const missing = props.fields.filter((field) => field.required && (form[field.key] == null || form[field.key] === ''))
  if (missing.length) {
    message.warning('请填写：' + missing.map((field) => field.label).join('、'))
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {}
    props.fields.forEach((field) => {
      const value = form[field.key]
      if (value !== undefined && value !== '') payload[field.key] = value
    })
    if (editing.value && props.update) {
      await props.update(String(editing.value.id), payload)
      message.success('已保存')
    } else if (props.create) {
      await props.create(payload)
      message.success('已新增')
    }
    modalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function toggleRow(record: Record<string, unknown>, enable: boolean) {
  if (!props.toggle) return
  try {
    await props.toggle(record, enable)
    message.success(enable ? '已启用' : '已停用')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(reload)
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>
