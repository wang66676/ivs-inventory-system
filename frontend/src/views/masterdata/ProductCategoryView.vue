<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="新增产品大项属于配置动作：选择库存追踪方式（数量 / 一物一档 VIN/SN）与状态机模板即可，不需要改代码。一物一档商品的状态流转由所选模板控制。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新增大项</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="false">
        <template #expandedRowRender="{ record }">
          <a-space direction="vertical" style="width:100%">
            <a-space>
              <b>属性模板</b>
              <span class="hint">驱动商品表单与 VIN/SN 库存的扩展字段（必填属性会在采购收货建档时校验）</span>
              <a-button v-if="canWrite" size="small" @click="openAttrCreate(record)">新增属性</a-button>
            </a-space>
            <a-table :data-source="record.attrDefs" row-key="id" size="small" :pagination="false"
                     :locale="{ emptyText: '暂无属性定义' }">
              <a-table-column title="编码" data-index="code" :width="140" />
              <a-table-column title="名称" data-index="name" :width="160" />
              <a-table-column title="类型" data-index="dataType" :width="110" />
              <a-table-column title="必填" :width="80">
                <template #default="{ record: attr }">
                  <a-tag :color="attr.required ? 'red' : 'default'">{{ attr.required ? '必填' : '选填' }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="选项" :width="220">
                <template #default="{ record: attr }">{{ attr.optionsJson ?? '-' }}</template>
              </a-table-column>
              <a-table-column title="操作" :width="80">
                <template #default="{ record: attr }">
                  <a v-if="canWrite" @click="removeAttr(record, attr)">删除</a>
                </template>
              </a-table-column>
            </a-table>
          </a-space>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'trackingMode'">
            <a-tag :color="record.trackingMode === 'SERIAL' ? 'blue' : 'default'">
              {{ trackingLabel(record.trackingMode) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a v-if="canWrite" @click="openEdit(record)">编辑</a>
              <a v-if="canWrite && record.status === 'ACTIVE'" @click="toggle(record, false)">停用</a>
              <a v-else-if="canWrite" @click="toggle(record, true)">启用</a>
              <a v-if="canWrite" style="color:#cf1322" @click="removeCategory(record)">删除</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="(editing ? '编辑' : '新增') + '产品大项'" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="680px" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="编码" required>
          <a-input v-model:value="form.code" :disabled="!!editing" placeholder="如 VEHICLE / BATTERY / PART" />
        </a-form-item>
        <a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="库存追踪方式" required>
          <a-select v-model:value="form.trackingMode" :options="trackingOptions" :disabled="!!editing && (editing.productCount ?? 0) > 0" />
          <div v-if="editing && (editing.productCount ?? 0) > 0" class="hint">该大项下已有商品，追踪方式不可修改</div>
        </a-form-item>
        <a-form-item v-if="form.trackingMode === 'SERIAL'" label="状态机模板" required>
          <a-select v-model:value="form.statusTemplate" :options="templateOptions" />
          <div class="hint">{{ templateHint }}</div>
        </a-form-item>
        <a-form-item label="计量单位"><a-input v-model:value="form.unit" placeholder="台 / 组 / 件" /></a-form-item>
        <a-form-item label="默认税率"><a-input-number v-model:value="form.defaultTaxRate" :min="0" :max="1" :step="0.01" style="width:100%" /></a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="attrModalOpen" title="新增属性" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="560px" @ok="submitAttr">
      <a-form layout="vertical">
        <a-form-item label="属性编码" required><a-input v-model:value="attrForm.code" placeholder="如 COLOR / CAPACITY" /></a-form-item>
        <a-form-item label="属性名称" required><a-input v-model:value="attrForm.name" /></a-form-item>
        <a-form-item label="数据类型">
          <a-select v-model:value="attrForm.dataType" :options="attrTypeOptions" />
        </a-form-item>
        <a-form-item label="是否必填"><a-switch v-model:checked="attrForm.required" /></a-form-item>
        <a-form-item label="选项（SELECT 类型填写 JSON 数组）">
          <a-textarea v-model:value="attrForm.optionsJson" :rows="2" placeholder='["红","白","蓝"]' />
        </a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="attrForm.sortOrder" :min="0" style="width:100%" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { categoryApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { TRACKING_MODE_LABELS } from '../../types/masterdata'
import type { AttrDef, Category, TrackingMode } from '../../types/masterdata'
const trackingLabel = (mode: string) => TRACKING_MODE_LABELS[mode as TrackingMode]

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')

const rows = ref<Category[]>([])
const modalOpen = ref(false)
const attrModalOpen = ref(false)
const saving = ref(false)
const editing = ref<Category | null>(null)
const attrCategory = ref<Category | null>(null)

const form = reactive<Record<string, unknown>>({ trackingMode: 'SERIAL', statusTemplate: 'VEHICLE', sortOrder: 0 })
const attrForm = reactive<Record<string, unknown>>({ dataType: 'STRING', required: false, sortOrder: 0 })

const columns = [
  { title: '编码', key: 'code', dataIndex: 'code', width: 120 },
  { title: '名称', key: 'name', dataIndex: 'name', width: 160 },
  { title: '追踪方式', key: 'trackingMode', width: 130 },
  { title: '状态机', key: 'statusTemplate', dataIndex: 'statusTemplate', width: 110 },
  { title: '单位', key: 'unit', dataIndex: 'unit', width: 80 },
  { title: '商品数', key: 'productCount', dataIndex: 'productCount', width: 90 },
  { title: '属性数', key: 'attrCount', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 190 },
  
]

const trackingOptions = Object.entries(TRACKING_MODE_LABELS).map(([value, label]) => ({ value, label }))
const templateOptions = [
  { value: 'VEHICLE', label: '整车流程（在途→到店→在库→锁定→已售→已交付，可退回/维修/报废）' },
  { value: 'BATTERY', label: '电池流程（同整车，支持租赁与退役）' },
  { value: 'SIMPLE', label: '简单流程（建档→在库→已售→已交付）' },
]
const attrTypeOptions = ['STRING', 'NUMBER', 'DATE', 'BOOL', 'SELECT'].map((value) => ({ value, label: value }))
const templateHint = computed(() => {
  const found = templateOptions.find((item) => item.value === form.statusTemplate)
  return found ? found.label : ''
})

async function reload() {
  try {
    rows.value = await categoryApi.list()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { code: undefined, name: undefined, trackingMode: 'SERIAL', statusTemplate: 'VEHICLE', unit: '台', sortOrder: 0, defaultTaxRate: undefined, remark: undefined })
  modalOpen.value = true
}

function openEdit(record: Category) {
  editing.value = record
  Object.assign(form, {
    code: record.code, name: record.name, trackingMode: record.trackingMode,
    statusTemplate: record.statusTemplate, unit: record.unit, sortOrder: record.sortOrder,
    defaultTaxRate: record.defaultTaxRate, remark: record.remark,
  })
  modalOpen.value = true
}

async function submit() {
  if (!form.name) { message.warning('请填写名称'); return }
  if (!editing.value && !form.code) { message.warning('请填写编码'); return }
  saving.value = true
  try {
    if (editing.value) {
      await categoryApi.update(editing.value.id, form)
    } else {
      await categoryApi.create(form)
    }
    message.success('已保存')
    modalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

function removeCategory(record: Category) {
  Modal.confirm({
    title: `确认删除产品大项「${record.name}」？`,
    content: '仅当该大项下没有商品时才能删除，删除后不可恢复。',
    okText: '确认删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      try { await categoryApi.delete(record.id); message.success('产品大项已删除'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}
async function toggle(record: Category, enable: boolean) {
  try {
    if (enable) await categoryApi.enable(record.id)
    else await categoryApi.disable(record.id)
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openAttrCreate(record: Category) {
  attrCategory.value = record
  Object.assign(attrForm, { code: undefined, name: undefined, dataType: 'STRING', required: false, optionsJson: undefined, sortOrder: 0 })
  attrModalOpen.value = true
}

async function submitAttr() {
  if (!attrCategory.value) return
  if (!attrForm.code || !attrForm.name) { message.warning('请填写属性编码与名称'); return }
  saving.value = true
  try {
    await categoryApi.createAttrDef(attrCategory.value.id, attrForm)
    message.success('已新增属性')
    attrModalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function removeAttr(_record: Category, attr: AttrDef) {
  try {
    await categoryApi.deleteAttrDef(attr.id)
    message.success('已删除属性')
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



