<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="盘点单创建时快照当前账面库存；录入实盘数并填写差异原因后过账。过账前会复核库存是否被其它业务修改，避免用过期账面数调整库存。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canAdjust" type="primary" @click="openCreate">新建盘点单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterWarehouse" :options="warehouseOptions" allow-clear show-search
                  placeholder="按仓库筛选" style="width:220px" @change="reload" />
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="状态"
                  style="width:140px" @change="reload" />
      </a-space>

      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1200 }">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="商品" :width="220">
              <template #default="{ record: line }">{{ productName(line.productId) }}</template>
            </a-table-column>
            <a-table-column title="批次" data-index="batchNo" :width="110" />
            <a-table-column title="账面数" :width="100">
              <template #default="{ record: line }">{{ qty(line.bookQty) }}</template>
            </a-table-column>
            <a-table-column title="实盘数" :width="100">
              <template #default="{ record: line }">{{ qty(line.actualQty) }}</template>
            </a-table-column>
            <a-table-column title="差异" :width="100">
              <template #default="{ record: line }">
                <a-tag :color="Number(line.differenceQty) === 0 ? 'default' : Number(line.differenceQty) > 0 ? 'blue' : 'red'">
                  {{ qty(line.differenceQty) }}
                </a-tag>
              </template>
            </a-table-column>
            <a-table-column title="单位成本" :width="120">
              <template #default="{ record: line }">{{ money(line.unitCost) }}</template>
            </a-table-column>
            <a-table-column title="差异原因" data-index="differenceReason" />
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'warehouse'">{{ warehouseName(record.warehouseId) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="STOCKTAKE_STATUS_COLORS[record.status as StocktakeStatus]">
              {{ STOCKTAKE_STATUS_LABELS[record.status as StocktakeStatus] }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'difference'">
            <span :class="{ loss: Number(record.totalDifferenceQty) < 0, gain: Number(record.totalDifferenceQty) > 0 }">
              {{ qty(record.totalDifferenceQty) }}
            </span>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="canAdjust">
              <a-button v-if="record.status === 'DRAFT'" size="small" @click="openEdit(record)">录入实盘</a-button>
              <a-button v-if="record.status === 'DRAFT'" size="small" type="primary"
                        @click="postStocktake(record)">过账</a-button>
              <a-button v-if="record.status === 'DRAFT' || record.status === 'PENDING_APPROVAL'" size="small" danger
                        @click="cancelStocktake(record)">取消</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="createOpen" title="新建库存盘点" :confirm-loading="saving" ok-text="生成盘点表"
             cancel-text="取消" @ok="create">
      <a-form layout="vertical">
        <a-form-item label="盘点仓库" required>
          <a-select v-model:value="createForm.warehouseId" :options="warehouseOptions" show-search />
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="createForm.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="editOpen" title="录入实盘数量" :confirm-loading="saving" ok-text="保存草稿"
             cancel-text="取消" width="1080px" @ok="saveLines">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="差异原因在过账前必填。账面数为创建盘点单时的快照；新增商品行会取当前库存作为账面数。" />
      <a-button size="small" style="margin-bottom:10px" @click="addLine">新增盘点行</a-button>
      <a-table :data-source="editLines" row-key="key" size="small" :pagination="false" :scroll="{ x: 1050 }">
        <a-table-column title="商品" :width="250">
          <template #default="{ record }">
            <a-select v-if="!record.id" v-model:value="record.productId" :options="productOptions" show-search
                      style="width:230px" placeholder="选择商品" />
            <span v-else>{{ productName(record.productId) }}</span>
          </template>
        </a-table-column>
        <a-table-column title="批次" :width="130">
          <template #default="{ record }">
            <a-input v-if="!record.id" v-model:value="record.batchNo" placeholder="无批次填 -" />
            <span v-else>{{ record.batchNo }}</span>
          </template>
        </a-table-column>
        <a-table-column title="账面数" :width="100">
          <template #default="{ record }">{{ qty(record.bookQty) }}</template>
        </a-table-column>
        <a-table-column title="实盘数" :width="140">
          <template #default="{ record }">
            <a-input-number v-model:value="record.actualQty" :min="0" :precision="3" style="width:120px" />
          </template>
        </a-table-column>
        <a-table-column title="差异" :width="100">
          <template #default="{ record }">{{ qty(diffOf(record)) }}</template>
        </a-table-column>
        <a-table-column title="单位成本" :width="140">
          <template #default="{ record }">
            <MoneyInput v-model:value="record.unitCost" :min="0" :precision="2" style="width:120px" />
          </template>
        </a-table-column>
        <a-table-column title="差异原因" :width="230">
          <template #default="{ record }">
            <a-input v-model:value="record.differenceReason" placeholder="有差异时必填" />
          </template>
        </a-table-column>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { stocktakeApi } from '../../api/stocktake'
import { productApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { STOCKTAKE_STATUS_COLORS, STOCKTAKE_STATUS_LABELS } from '../../types/stocktake'
import type { Stocktake, StocktakeLine, StocktakeStatus } from '../../types/stocktake'

const auth = useAuthStore()
const canAdjust = auth.can('IVS_STOCK_ADJUST')
const rows = ref<Stocktake[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const products = ref<Record<string, string>>({})
const warehouseNames = ref<Record<string, string>>({})
const filterWarehouse = ref<string | undefined>()
const filterStatus = ref<StocktakeStatus | undefined>()
const createOpen = ref(false)
const editOpen = ref(false)
const saving = ref(false)
const current = ref<Stocktake>()
const editLines = ref<any[]>([])
const createForm = reactive<Record<string, any>>({})

const statusOptions = Object.entries(STOCKTAKE_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const columns = [
  { title: '盘点单号', key: 'stocktakeNo', dataIndex: 'stocktakeNo', width: 140 },
  { title: '仓库', key: 'warehouse', width: 180 },
  { title: '状态', key: 'status', width: 100 },
  { title: '明细数', key: 'lineCount', dataIndex: 'lineCount', width: 90 },
  { title: '差异行', key: 'differenceCount', dataIndex: 'differenceCount', width: 90 },
  { title: '差异数量合计', key: 'difference', width: 130 },
  { title: '备注', key: 'remark', dataIndex: 'remark', width: 180 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 230, fixed: 'right' },
]

function productName(id: string) { return products.value[id] || id }
function warehouseName(id: string) { return warehouseNames.value[id] || id }
function qty(value?: number) { return Number(value || 0).toFixed(3) }
function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function diffOf(line: any) {
  const diff = Number(line.actualQty || 0) - Number(line.bookQty || 0)
  return Math.abs(diff) < 1e-9 ? 0 : diff
}

function openCreate() {
  Object.keys(createForm).forEach((key) => delete createForm[key])
  createOpen.value = true
}

async function create() {
  if (!createForm.warehouseId) { message.warning('请选择盘点仓库'); return }
  saving.value = true
  try {
    const created = await stocktakeApi.create({
      warehouseId: createForm.warehouseId, remark: createForm.remark || null, lines: null,
    })
    message.success(`盘点单 ${created.stocktakeNo} 已生成，共 ${created.lineCount} 行`)
    createOpen.value = false
    await reload()
    openEdit(created)
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function openEdit(record: Stocktake) {
  current.value = record
  editLines.value = record.lines.map((line: StocktakeLine) => ({ ...line, key: line.id }))
  editOpen.value = true
}

function addLine() {
  editLines.value.push({
    key: 'new-' + Date.now() + '-' + editLines.value.length, productId: undefined, batchNo: '-',
    bookQty: 0, actualQty: 0, unitCost: 0, differenceReason: '',
  })
}

async function saveLines() {
  if (!current.value) return
  const valid = editLines.value.filter((line) => line.productId)
  if (!valid.length) { message.warning('盘点明细不能为空'); return }
  for (const line of valid) {
    if (diffOf(line) !== 0 && !line.differenceReason?.trim()) {
      message.warning(`商品 ${productName(line.productId)} 存在差异，请填写差异原因`); return
    }
  }
  saving.value = true
  try {
    await stocktakeApi.update(current.value.id, {
      lines: valid.map((line) => ({
        productId: line.productId, batchNo: line.batchNo || '-', actualQty: line.actualQty,
        unitCost: line.unitCost, differenceReason: line.differenceReason || null,
      })),
    })
    message.success('实盘数量已保存')
    editOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function postStocktake(record: Stocktake) {
  Modal.confirm({
    title: `确认过账 ${record.stocktakeNo}？`,
    content: '过账后将写库存台账并调整余额，不可修改。',
    okText: '确认过账', cancelText: '取消',
    onOk: async () => {
      try {
        const result = await stocktakeApi.post(record.id)
        message.success(result.status === 'PENDING_APPROVAL' ? '盘点差异已提交审批，审批通过后自动过账' : '盘点已过账')
        await reload()
      }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

function cancelStocktake(record: Stocktake) {
  Modal.confirm({
    title: `取消盘点单 ${record.stocktakeNo}？`,
    okText: '确认取消', cancelText: '返回',
    onOk: async () => {
      try { await stocktakeApi.cancel(record.id); message.success('盘点单已取消'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

async function reload() {
  try {
    rows.value = await stocktakeApi.list({ warehouseId: filterWarehouse.value, status: filterStatus.value })
  } catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [warehouses, productRows] = await Promise.all([warehouseApi.list(), productApi.list()])
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    warehouseNames.value = Object.fromEntries(warehouses.map((item) => [item.id, item.name]))
    productOptions.value = productRows.map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
    products.value = Object.fromEntries(productRows.map((item) => [item.id, `${item.name}（${item.productNo}）`]))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.loss { color: #cf1322; font-weight: 600; }
.gain { color: #1677ff; font-weight: 600; }
</style>



