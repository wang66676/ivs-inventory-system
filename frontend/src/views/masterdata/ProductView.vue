<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="新增商品只需填写：商品、车系、型号、单位、税率。商品内部名称由车系和型号自动生成；批量导入使用同一模板。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新增商品</a-button>
        <a-button v-if="canWrite" @click="importOpen = true">批量上传</a-button>
        <a-button @click="downloadTemplate">下载模板</a-button>
        <a-button @click="exportProducts">导出商品</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterCategoryId" :options="categoryOptions" allow-clear
                  placeholder="按商品筛选" style="width:200px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="商品编码 / 型号 / 车系" allow-clear
                 style="width:220px" @press-enter="reload" />
        <a-button @click="reload">查询</a-button>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1300 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taxRate'">{{ formatTax(record.taxRate) }}</template>
          <template v-else-if="column.key === 'trackingMode'">
            <a-tag :color="record.trackingMode === 'SERIAL' ? 'blue' : 'default'">
              {{ TRACKING_MODE_LABELS[record.trackingMode as TrackingMode] }}
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
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="editing ? '编辑商品' : '新增商品'" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="760px" @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="商品" required>
              <a-select v-model:value="form.categoryId" :options="categoryOptions" :disabled="!!editing"
                        placeholder="选择商品大类" @change="onCategoryChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="车系"><a-input v-model:value="form.series" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="型号"><a-input v-model:value="form.model" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="单位"><a-input v-model:value="form.unit" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="税率（%）">
              <a-input-number v-model:value="form.taxRatePercent" :min="0" :max="100" :precision="2" style="width:100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <template v-if="editing">
          <a-divider orientation="left" style="font-size:13px">更多字段</a-divider>
          <a-row :gutter="12">
            <a-col :span="8"><a-form-item label="商品编码"><a-input :value="editing.productNo" disabled /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="品牌"><a-input v-model:value="form.brand" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="配置代码"><a-input v-model:value="form.configCode" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="最低限价"><MoneyInput v-model:value="form.minPrice" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="指导价"><MoneyInput v-model:value="form.salePriceGuide" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="参考进价"><MoneyInput v-model:value="form.purchasePriceRef" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col>
          </a-row>
        </template>
        <a-divider v-if="editing && currentAttrDefs.length" orientation="left" style="font-size:13px">品类扩展属性</a-divider>
        <a-row v-if="editing && currentAttrDefs.length" :gutter="12">
          <a-col v-for="def in currentAttrDefs" :key="def.id" :span="12">
            <a-form-item :label="def.name + (def.required ? ' *' : '')">
              <a-select v-if="def.dataType === 'SELECT'" v-model:value="attrs[def.code]" :options="optionsOf(def)" allow-clear />
              <a-input v-else v-model:value="attrs[def.code]" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item v-if="editing" label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="importOpen" title="批量上传商品" footer="" width="760px">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="请先下载模板，按“商品、车系、型号、单位、税率”填写；商品编码和内部名称会自动生成。" />
      <a-upload :before-upload="handleFile" :show-upload-list="false" accept=".csv,text/csv">
        <a-button type="primary" :loading="importing">选择 CSV 文件</a-button>
      </a-upload>
      <a-result v-if="importResult" style="margin-top:12px"
                :status="importResult.failed ? 'warning' : 'success'"
                :title="`导入完成：成功 ${importResult.created} 条，失败 ${importResult.failed} 条`">
        <template #extra>
          <ul v-if="importResult.errors?.length" class="errors">
            <li v-for="error in importResult.errors" :key="error">{{ error }}</li>
          </ul>
        </template>
      </a-result>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { categoryApi, productApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { TRACKING_MODE_LABELS } from '../../types/masterdata'
import type { AttrDef, Category, Product, TrackingMode } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')
const rows = ref<Product[]>([])
const categories = ref<Category[]>([])
const categoryOptions = ref<{ label: string; value: string }[]>([])
const filterCategoryId = ref<string>()
const keyword = ref('')
const modalOpen = ref(false)
const saving = ref(false)
const editing = ref<Product | null>(null)
const form = reactive<Record<string, any>>({})
const attrs = reactive<Record<string, any>>({})
const currentAttrDefs = ref<AttrDef[]>([])
const importOpen = ref(false)
const importing = ref(false)
const importResult = ref<{ created: number; failed: number; errors: string[]; items: Product[] }>()

const columns = [
  { title: '商品编码', key: 'productNo', dataIndex: 'productNo', width: 130 },
  { title: '商品', key: 'categoryName', dataIndex: 'categoryName', width: 120 },
  { title: '车系', key: 'series', dataIndex: 'series', width: 130 },
  { title: '型号', key: 'model', dataIndex: 'model', width: 140 },
  { title: '单位', key: 'unit', dataIndex: 'unit', width: 80 },
  { title: '税率', key: 'taxRate', width: 90 },
  { title: '追踪', key: 'trackingMode', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'actions', width: 130 },
]

function formatTax(value?: number) { return value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%` }
function optionsOf(def: AttrDef) {
  if (!def.optionsJson) return []
  try { return (JSON.parse(def.optionsJson) as string[]).map((x) => ({ label: x, value: x })) } catch { return [] }
}

async function reload() {
  try { rows.value = await productApi.list({ categoryId: filterCategoryId.value, keyword: keyword.value || undefined }) }
  catch (error) { message.error((error as Error).message) }
}

async function loadCategories() {
  categories.value = await categoryApi.list()
  categoryOptions.value = categories.value.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
}

function onCategoryChange() {
  // 新增商品不再要求填写品类扩展属性；仅编辑旧商品时保留历史属性展示。
  currentAttrDefs.value = editing.value
    ? categories.value.find((item) => item.id === form.categoryId)?.attrDefs ?? []
    : []
}

function openCreate() {
  editing.value = null
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(attrs).forEach((key) => delete attrs[key])
  currentAttrDefs.value = []
  form.unit = '台'
  form.taxRatePercent = 13
  modalOpen.value = true
}

function openEdit(record: Product) {
  editing.value = record
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(attrs).forEach((key) => delete attrs[key])
  Object.assign(form, {
    categoryId: record.categoryId, series: record.series, model: record.model,
    unit: record.unit, taxRatePercent: record.taxRate == null ? 13 : Number(record.taxRate) * 100,
    brand: record.brand, configCode: record.configCode, minPrice: record.minPrice,
    salePriceGuide: record.salePriceGuide, purchasePriceRef: record.purchasePriceRef, remark: record.remark,
  })
  currentAttrDefs.value = record.attrDefs ?? []
  if (record.specJson) { try { Object.assign(attrs, JSON.parse(record.specJson)) } catch {} }
  modalOpen.value = true
}

async function submit() {
  if (!form.categoryId) { message.warning('请选择商品'); return }
  const missing = editing.value
    ? currentAttrDefs.value.filter((def) => def.required && !attrs[def.code])
    : []
  if (missing.length) { message.warning('请填写扩展属性：' + missing.map((def) => def.name).join('、')); return }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      categoryId: form.categoryId, series: form.series, model: form.model,
      unit: form.unit, taxRate: form.taxRatePercent == null ? null : Number(form.taxRatePercent) / 100,
      brand: form.brand, configCode: form.configCode, minPrice: form.minPrice,
      salePriceGuide: form.salePriceGuide, purchasePriceRef: form.purchasePriceRef, remark: form.remark,
    }
    if (editing.value && Object.keys(attrs).length) payload.specJson = JSON.stringify(attrs)
    if (editing.value) await productApi.update(editing.value.id, payload)
    else await productApi.create(payload)
    message.success('已保存')
    modalOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function toggle(record: Product, enable: boolean) {
  try { if (enable) await productApi.enable(record.id); else await productApi.disable(record.id); await reload() }
  catch (error) { message.error((error as Error).message) }
}

function downloadTemplate() {
  const csv = '\ufeff商品,车系,型号,单位,税率\n示例商品,示例车系,示例型号,台,13\n'
  downloadBlob(csv, '商品导入模板.csv', 'text/csv;charset=utf-8')
}

async function exportProducts() {
  try {
    const blob = await productApi.exportCsv()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob as Blob)
    link.download = '商品列表.csv'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (error) { message.error((error as Error).message) }
}

async function handleFile(file: File) {
  importing.value = true
  importResult.value = undefined
  try {
    const text = await file.text()
    const lines = parseCsv(text.replace(/^\ufeff/, ''))
    const rowsToImport = lines.slice(1).filter((row) => row.some((cell) => cell.trim())).map((row) => ({
      categoryCode: row[0]?.trim(), series: row[1]?.trim(), model: row[2]?.trim(),
      unit: row[3]?.trim(), taxRate: row[4] ? Number(row[4]) : null,
    }))
    if (!rowsToImport.length) throw new Error('CSV 没有有效数据行')
    const result = await productApi.batchImport(rowsToImport) as any
    importResult.value = result
    if (!result.failed) message.success(`商品导入成功：${result.created} 条`)
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { importing.value = false }
  return false
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = []; let row: string[] = []; let cell = ''; let quoted = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++ }
      else if (ch === '"') quoted = false
      else cell += ch
    } else if (ch === '"') quoted = true
    else if (ch === ',') { row.push(cell); cell = '' }
    else if (ch === '\n') { row.push(cell); rows.push(row); row = []; cell = '' }
    else if (ch !== '\r') cell += ch
  }
  if (cell || row.length) { row.push(cell); rows.push(row) }
  return rows
}

function downloadBlob(content: string, name: string, type: string) {
  const blob = new Blob([content], { type })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = name
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(async () => { await loadCategories(); await reload() })
</script>

<style scoped>
.errors { margin: 0; padding-left: 18px; color: #cf1322; }
</style>





