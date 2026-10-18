<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="序列号档案：整车 VIN 与动力电池 SN 一物一档。建档只负责身份、属性与状态流转；库存数量与台账由 M2 库存模块承担。VIN 会自动转大写并做全局唯一校验。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">建档</a-button>
        <a-button v-if="canWrite" @click="openImport">批量导入</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:160px" @change="reload" />
        <a-select v-model:value="filterWarehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选"
                  style="width:180px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="VIN / 电池 SN" allow-clear style="width:220px"
                 @press-enter="reload" />
        <a-button @click="reload">查询</a-button>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1200 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'ownershipType'">
            {{ record.ownershipType === 'OWNED' ? '自有' : record.ownershipType === 'LEASE_TO_OWN' ? '以租代售' : '租赁' }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-dropdown v-if="canWrite && record.allowedNextStatuses?.length">
              <a>状态流转 <DownOutlined /></a>
              <template #overlay>
                <a-menu @click="onStatusMenu(record, $event)">
                  <a-menu-item v-for="next in record.allowedNextStatuses" :key="next">
                    → {{ statusLabel(next) }}
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <span v-else class="hint">—</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" title="序列号建档" :confirm-loading="saving" ok-text="建档" cancel-text="取消"
             width="720px" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="商品（仅序列号管理的大项可选）" required>
          <a-select v-model:value="form.productId" :options="serialProductOptions" show-search
                    @change="onProductChange" />
        </a-form-item>
        <a-form-item label="序列号（VIN / 电池 SN）" required>
          <a-input v-model:value="form.serialNo" placeholder="17 位 VIN 或电池 SN，自动转大写" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="入库仓库"><a-select v-model:value="form.warehouseId" :options="warehouseOptions" allow-clear /></a-form-item></a-col>
          <a-col :span="6"><a-form-item label="库位"><a-input v-model:value="form.locationCode" /></a-form-item></a-col>
          <a-col :span="6"><a-form-item label="批次"><a-input v-model:value="form.batchNo" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="采购单价"><a-input-number v-model:value="form.purchasePrice" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8">
            <a-form-item label="入库日期（填写即视为在库）">
              <a-date-picker v-model:value="form.inboundDate" value-format="YYYY-MM-DD" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="所有权"><a-select v-model:value="form.ownershipType" :options="ownershipOptions" /></a-form-item></a-col>
        </a-row>

        <a-divider orientation="left" style="font-size:13px">品类扩展属性</a-divider>
        <a-empty v-if="!currentAttrDefs.length" description="该商品所属大项暂无属性定义" />
        <a-row v-else :gutter="12">
          <a-col v-for="def in currentAttrDefs" :key="def.id" :span="12">
            <a-form-item :label="def.name + (def.required ? ' *' : '')">
              <a-select v-if="def.dataType === 'SELECT'" v-model:value="attrs[def.code]" :options="optionsOf(def)" allow-clear />
              <a-input v-else v-model:value="attrs[def.code]" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="importOpen" title="批量导入序列号" :confirm-loading="saving" ok-text="导入" cancel-text="取消"
             width="620px" @ok="submitImport">
      <a-form layout="vertical">
        <a-form-item label="商品" required><a-select v-model:value="importForm.productId" :options="serialProductOptions" /></a-form-item>
        <a-form-item label="入库仓库"><a-select v-model:value="importForm.warehouseId" :options="warehouseOptions" allow-clear /></a-form-item>
        <a-form-item label="序列号（每行一个）" required>
          <a-textarea v-model:value="importForm.serialNos" :rows="8" placeholder="LZZ1CLWB0NA000001&#10;LZZ1CLWB0NA000002" />
        </a-form-item>
        <a-alert v-if="importResult" :type="importResult.failed ? 'warning' : 'success'" show-icon
                 :message="`成功 ${importResult.created} 条，失败 ${importResult.failed} 条`" />
        <ul v-if="importResult?.errors?.length" class="hint" style="margin-top:8px">
          <li v-for="(err, index) in importResult.errors" :key="index">{{ err }}</li>
        </ul>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import { productApi, serialApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { SERIAL_STATUS_LABELS } from '../../types/masterdata'
import type { AttrDef, BatchImportResult, Product, ProductSerial, SerialStatus } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')

const rows = ref<ProductSerial[]>([])
const products = ref<Product[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const filterStatus = ref<string | undefined>(undefined)
const filterWarehouseId = ref<string | undefined>(undefined)
const keyword = ref('')
const modalOpen = ref(false)
const importOpen = ref(false)
const saving = ref(false)
const importResult = ref<BatchImportResult | null>(null)
const currentAttrDefs = ref<AttrDef[]>([])

const form = reactive<Record<string, any>>({ ownershipType: 'OWNED' })
const attrs = reactive<Record<string, any>>({})
const importForm = reactive<Record<string, any>>({})

const columns = [
  { title: '序列号', key: 'serialNo', dataIndex: 'serialNo', width: 200 },
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 200 },
  { title: '大项', key: 'categoryCode', dataIndex: 'categoryCode', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '采购单价', key: 'purchasePrice', dataIndex: 'purchasePrice', width: 120 },
  { title: '入库时间', key: 'inboundAt', width: 170,
    render: (row: Record<string, unknown>) => (row.inboundAt ? String(row.inboundAt).replace('T', ' ').slice(0, 19) : '-') },
  { title: '所有权', key: 'ownershipType', width: 100 },
  { title: '操作', key: 'actions', width: 120 },
]

const statusLabel = (status: string) => SERIAL_STATUS_LABELS[status as SerialStatus]
const onStatusMenu = (record: ProductSerial, info: { key: string }) => moveStatus(record, info.key)
const statusOptions = Object.entries(SERIAL_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const ownershipOptions = [
  { value: 'OWNED', label: '自有' },
  { value: 'LEASE_TO_OWN', label: '以租代售' },
  { value: 'LEASE_D', label: '租赁（二期）' },
]
const serialProductOptions = ref<{ label: string; value: string }[]>([])

function statusColor(status: SerialStatus) {
  if (status === 'IN_STOCK') return 'green'
  if (status === 'SCRAPPED') return 'red'
  if (status === 'SOLD' || status === 'DELIVERED') return 'blue'
  if (status === 'LOCKED') return 'orange'
  return 'default'
}

function optionsOf(def: AttrDef) {
  if (!def.optionsJson) return []
  try {
    return (JSON.parse(def.optionsJson) as string[]).map((item) => ({ label: item, value: item }))
  } catch {
    return []
  }
}

async function reload() {
  try {
    rows.value = await serialApi.list({
      keyword: keyword.value || undefined,
      status: filterStatus.value,
      warehouseId: filterWarehouseId.value,
    })
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function loadRefs() {
  const [warehouses, allProducts] = await Promise.all([warehouseApi.list(), productApi.list()])
  warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  products.value = allProducts
  serialProductOptions.value = allProducts
    .filter((item) => item.trackingMode === 'SERIAL' && item.status === 'ACTIVE')
    .map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
}

function onProductChange() {
  const product = products.value.find((item) => item.id === form.productId)
  currentAttrDefs.value = product?.attrDefs ?? []
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(attrs).forEach((key) => delete attrs[key])
  currentAttrDefs.value = []
  form.ownershipType = 'OWNED'
  modalOpen.value = true
}

async function submit() {
  if (!form.productId) { message.warning('请选择商品'); return }
  if (!form.serialNo) { message.warning('请填写序列号'); return }
  const missing = currentAttrDefs.value.filter((def) => def.required && !attrs[def.code])
  if (missing.length) { message.warning('请填写扩展属性：' + missing.map((def) => def.name).join('、')); return }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      serialNo: form.serialNo, productId: form.productId, warehouseId: form.warehouseId,
      locationCode: form.locationCode, batchNo: form.batchNo, purchasePrice: form.purchasePrice,
      ownershipType: form.ownershipType === 'LEASE_D' ? 'LEASED' : form.ownershipType, remark: form.remark,
    }
    if (form.inboundDate) payload.inboundAt = `${form.inboundDate}T00:00:00Z`
    if (Object.keys(attrs).length) payload.attrsJson = JSON.stringify(attrs)
    await serialApi.create(payload)
    message.success('已建档')
    modalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

function openImport() {
  Object.keys(importForm).forEach((key) => delete importForm[key])
  importResult.value = null
  importOpen.value = true
}

async function submitImport() {
  if (!importForm.productId) { message.warning('请选择商品'); return }
  const serialNos = String(importForm.serialNos ?? '').split('\n').map((item) => item.trim()).filter(Boolean)
  if (!serialNos.length) { message.warning('请填写至少一个序列号'); return }
  saving.value = true
  try {
    const commands = serialNos.map((serialNo) => ({
      serialNo, productId: importForm.productId, warehouseId: importForm.warehouseId,
    }))
    importResult.value = await serialApi.batchImport(commands)
    if (!importResult.value.failed) {
      message.success(`导入完成：${importResult.value.created} 条`)
      importOpen.value = false
    }
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function moveStatus(record: ProductSerial, target: string) {
  try {
    await serialApi.moveStatus(record.id, target)
    message.success('状态已更新')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  await loadRefs()
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>