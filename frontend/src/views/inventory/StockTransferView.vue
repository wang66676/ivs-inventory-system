<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="调拨流程：草稿 → 调出仓审核 → 发出（在途）→ 调入仓签收。发出时校验调出仓可用库存；签收数量少于调拨数量时必须填写差异原因。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canTransfer" type="primary" @click="openCreate">新建调拨单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterWarehouse" :options="warehouseOptions" allow-clear show-search
                  placeholder="按仓库筛选" style="width:220px" @change="reload" />
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="状态"
                  style="width:140px" @change="reload" />
      </a-space>

      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1350 }">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="商品" :width="220">
              <template #default="{ record: line }">{{ productName(line.productId) }}</template>
            </a-table-column>
            <a-table-column title="批次" data-index="batchNo" :width="110" />
            <a-table-column title="调拨数量" :width="110">
              <template #default="{ record: line }">{{ qty(line.quantity) }}</template>
            </a-table-column>
            <a-table-column title="已签收" :width="110">
              <template #default="{ record: line }">{{ qty(line.receivedQuantity) }}</template>
            </a-table-column>
            <a-table-column title="差异" :width="100">
              <template #default="{ record: line }">
                <a-tag :color="Number(line.differenceQuantity) === 0 ? 'default' : 'red'">{{ qty(line.differenceQuantity) }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="单位成本" :width="120">
              <template #default="{ record: line }">{{ money(line.unitCost) }}</template>
            </a-table-column>
            <a-table-column title="差异原因" data-index="differenceReason" />
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'route'">
            {{ warehouseName(record.fromWarehouseId) }} → {{ warehouseName(record.toWarehouseId) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="TRANSFER_STATUS_COLORS[record.status as StockTransferStatus]">
              {{ TRANSFER_STATUS_LABELS[record.status as StockTransferStatus] }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'quantity'">{{ qty(record.totalQuantity) }}</template>
          <template v-else-if="column.key === 'received'">{{ qty(record.receivedQuantity) }}</template>
          <template v-else-if="column.key === 'difference'">
            <a-tag :color="Number(record.differenceQuantity) === 0 ? 'default' : 'red'">{{ qty(record.differenceQuantity) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="canTransfer" wrap>
              <a-button v-if="record.status === 'DRAFT'" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button v-if="record.status === 'DRAFT'" size="small" type="primary" @click="approve(record)">审核</a-button>
              <a-button v-if="record.status === 'APPROVED'" size="small" type="primary" @click="dispatch(record)">发出</a-button>
              <a-button v-if="record.status === 'IN_TRANSIT'" size="small" type="primary" @click="openReceive(record)">签收</a-button>
              <a-button v-if="record.status === 'DRAFT' || record.status === 'APPROVED'" size="small" danger
                        @click="cancel(record)">取消</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" :title="editingId ? '编辑调拨单' : '新建调拨单'" :confirm-loading="saving"
             ok-text="保存草稿" cancel-text="取消" width="980px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="调出仓" required>
              <a-select v-model:value="form.fromWarehouseId" :options="warehouseOptions" show-search :disabled="!!editingId" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="调入仓" required>
              <a-select v-model:value="form.toWarehouseId" :options="warehouseOptions" show-search :disabled="!!editingId" />
            </a-form-item>
          </a-col>
          <a-col :span="24"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
      </a-form>
      <a-button size="small" style="margin-bottom:10px" @click="addLine">新增调拨行</a-button>
      <a-table :data-source="form.lines" row-key="key" size="small" :pagination="false">
        <a-table-column title="商品" :width="320">
          <template #default="{ record }">
            <a-select v-model:value="record.productId" :options="productOptions" show-search style="width:300px"
                      :disabled="editingId && record.id" />
          </template>
        </a-table-column>
        <a-table-column title="批次" :width="140">
          <template #default="{ record }"><a-input v-model:value="record.batchNo" :disabled="editingId && record.id" /></template>
        </a-table-column>
        <a-table-column title="数量" :width="160">
          <template #default="{ record }">
            <a-input-number v-model:value="record.quantity" :min="0.001" :precision="3" style="width:140px" />
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="100">
          <template #default="{ index }">
            <a-button size="small" danger @click="form.lines.splice(index, 1)">删除</a-button>
          </template>
        </a-table-column>
      </a-table>
    </a-modal>

    <a-modal v-model:open="receiveOpen" title="调入仓签收" :confirm-loading="saving" ok-text="确认签收"
             cancel-text="取消" width="900px" @ok="submitReceive">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="签收数量不能大于调拨数量；少于调拨数量时必须填写差异原因。" />
      <a-table :data-source="receiveLines" row-key="lineId" size="small" :pagination="false">
        <a-table-column title="商品" :width="220">
          <template #default="{ record }">{{ productName(record.productId) }}</template>
        </a-table-column>
        <a-table-column title="调拨数量" :width="110">
          <template #default="{ record }">{{ qty(record.quantity) }}</template>
        </a-table-column>
        <a-table-column title="签收数量" :width="150">
          <template #default="{ record }">
            <a-input-number v-model:value="record.receivedQuantity" :min="0" :max="record.quantity" :precision="3" style="width:130px" />
          </template>
        </a-table-column>
        <a-table-column title="差异原因">
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
import { transferApi } from '../../api/stocktransfer'
import { productApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { TRANSFER_STATUS_COLORS, TRANSFER_STATUS_LABELS } from '../../types/stocktransfer'
import type { StockTransfer, StockTransferLine, StockTransferStatus } from '../../types/stocktransfer'

const auth = useAuthStore()
const canTransfer = auth.can('IVS_STOCK_TRANSFER')
const rows = ref<StockTransfer[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const warehouseNames = ref<Record<string, string>>({})
const products = ref<Record<string, string>>({})
const filterWarehouse = ref<string | undefined>()
const filterStatus = ref<StockTransferStatus | undefined>()
const saving = ref(false)
const formOpen = ref(false)
const editingId = ref<string>()
const receiveOpen = ref(false)
const current = ref<StockTransfer>()
const receiveLines = ref<any[]>([])
const form = reactive<Record<string, any>>({ lines: [] })

const statusOptions = Object.entries(TRANSFER_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const columns = [
  { title: '调拨单号', key: 'transferNo', dataIndex: 'transferNo', width: 140 },
  { title: '调拨路径', key: 'route', width: 260 },
  { title: '状态', key: 'status', width: 100 },
  { title: '明细数', key: 'lineCount', dataIndex: 'lineCount', width: 90 },
  { title: '调拨数量', key: 'quantity', width: 110 },
  { title: '已签收', key: 'received', width: 110 },
  { title: '差异', key: 'difference', width: 100 },
  { title: '备注', key: 'remark', dataIndex: 'remark', width: 160 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 240, fixed: 'right' },
]

function productName(id: string) { return products.value[id] || id }
function warehouseName(id: string) { return warehouseNames.value[id] || id }
function qty(value?: number) { return Number(value || 0).toFixed(3) }
function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }

function newLine() {
  return { key: 'new-' + Date.now() + '-' + Math.random(), productId: undefined, batchNo: '-', quantity: 1 }
}

function openCreate() {
  editingId.value = undefined
  Object.keys(form).forEach((key) => delete form[key])
  form.lines = [newLine()]
  formOpen.value = true
}

function openEdit(record: StockTransfer) {
  editingId.value = record.id
  Object.keys(form).forEach((key) => delete form[key])
  form.fromWarehouseId = record.fromWarehouseId
  form.toWarehouseId = record.toWarehouseId
  form.remark = record.remark
  form.lines = record.lines.map((line: StockTransferLine) => ({ ...line, key: line.id }))
  formOpen.value = true
}

function addLine() { form.lines.push(newLine()) }

async function save() {
  if (!form.fromWarehouseId || !form.toWarehouseId) { message.warning('请选择调出仓与调入仓'); return }
  if (form.fromWarehouseId === form.toWarehouseId) { message.warning('调出仓与调入仓不能相同'); return }
  const lines = (form.lines as any[]).filter((line) => line.productId && Number(line.quantity) > 0)
  if (!lines.length) { message.warning('至少需要一条有效调拨明细'); return }
  saving.value = true
  try {
    const body = {
      fromWarehouseId: form.fromWarehouseId, toWarehouseId: form.toWarehouseId, remark: form.remark || null,
      lines: lines.map((line) => ({ productId: line.productId, batchNo: line.batchNo || '-', quantity: line.quantity })),
    }
    if (editingId.value) await transferApi.update(editingId.value, { lines: body.lines })
    else await transferApi.create(body)
    message.success('调拨单已保存')
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function approve(record: StockTransfer) {
  Modal.confirm({
    title: `审核调拨单 ${record.transferNo}？`, okText: '确认审核', cancelText: '取消',
    onOk: async () => {
      try { await transferApi.approve(record.id); message.success('调拨单已审核'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

function dispatch(record: StockTransfer) {
  Modal.confirm({
    title: `确认发出 ${record.transferNo}？`,
    content: '发出后将从调出仓扣减库存，调拨单进入在途。',
    okText: '确认发出', cancelText: '取消',
    onOk: async () => {
      try { await transferApi.dispatch(record.id); message.success('调拨已发出，进入在途'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

function openReceive(record: StockTransfer) {
  current.value = record
  receiveLines.value = record.lines.map((line) => ({
    lineId: line.id, productId: line.productId, quantity: Number(line.quantity),
    receivedQuantity: Number(line.quantity), differenceReason: '',
  }))
  receiveOpen.value = true
}

async function submitReceive() {
  if (!current.value) return
  for (const line of receiveLines.value) {
    if (Number(line.receivedQuantity) < Number(line.quantity) && !line.differenceReason?.trim()) {
      message.warning(`商品 ${productName(line.productId)} 存在签收差异，请填写原因`); return
    }
  }
  saving.value = true
  try {
    await transferApi.receive(current.value.id, {
      lines: receiveLines.value.map((line) => ({
        lineId: line.lineId, receivedQuantity: line.receivedQuantity,
        differenceReason: line.differenceReason || null,
      })),
    })
    message.success('调拨已签收')
    receiveOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function cancel(record: StockTransfer) {
  Modal.confirm({
    title: `取消调拨单 ${record.transferNo}？`, okText: '确认取消', cancelText: '返回',
    onOk: async () => {
      try { await transferApi.cancel(record.id); message.success('调拨单已取消'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

async function reload() {
  try {
    rows.value = await transferApi.list({ warehouseId: filterWarehouse.value, status: filterStatus.value })
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
