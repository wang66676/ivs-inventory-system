<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="销售退货过账后商品回库并恢复可售，同时冲减对应出库应收；若客户已多付，系统登记应退金额。VIN / SN 商品需逐台选择或填写。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建销售退货</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterCustomer" :options="customerOptions" allow-clear show-search
                  placeholder="按客户筛选" style="width:220px" @change="reload" />
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="状态"
                  style="width:140px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1300 }">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="商品" :width="220">
              <template #default="{ record: line }">{{ productName(line.productId) }}</template>
            </a-table-column>
            <a-table-column title="数量" :width="100">
              <template #default="{ record: line }">{{ qty(line.quantity) }}</template>
            </a-table-column>
            <a-table-column title="售价" :width="110">
              <template #default="{ record: line }">{{ money(line.unitPrice) }}</template>
            </a-table-column>
            <a-table-column title="退货金额" :width="120">
              <template #default="{ record: line }">{{ money(line.amount) }}</template>
            </a-table-column>
            <a-table-column title="成本" :width="120">
              <template #default="{ record: line }">{{ money(line.costAmount) }}</template>
            </a-table-column>
            <a-table-column title="批次" data-index="batchNo" :width="100" />
            <a-table-column title="序列号">
              <template #default="{ record: line }">
                <a-tag v-for="serial in line.serialNos" :key="serial">{{ serial }}</a-tag>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'POSTED' ? 'green' : record.status === 'CANCELLED' ? 'red' : 'orange'">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'amount'">{{ money(record.totalAmount) }}</template>
          <template v-else-if="column.key === 'receivable'">{{ money(record.receivableAdjustmentAmount) }}</template>
          <template v-else-if="column.key === 'refund'">{{ money(record.refundAmount) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="canPost">
              <a-button v-if="record.status === 'DRAFT'" size="small" type="primary" @click="postReturn(record)">过账</a-button>
              <a-button v-if="record.status === 'DRAFT'" size="small" danger @click="cancelReturn(record)">取消</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="新建销售退货" :confirm-loading="saving" ok-text="保存草稿"
             cancel-text="取消" width="1080px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="客户" required><a-select v-model:value="form.customerId" :options="customerOptions" show-search /></a-form-item></a-col>
          <a-col :span="8">
            <a-form-item label="关联出库单（用于冲减应收）">
              <a-select v-model:value="form.deliveryId" :options="deliveryOptions" allow-clear show-search
                        @change="onDeliveryChange" />
            </a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="退货仓库" required><a-select v-model:value="form.warehouseId" :options="warehouseOptions" show-search /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="退货原因"><a-input v-model:value="form.reason" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
      </a-form>
      <a-button size="small" style="margin-bottom:10px" @click="addLine">新增退货行</a-button>
      <a-table :data-source="form.lines" row-key="key" size="small" :pagination="false" :scroll="{ x: 1050 }">
        <a-table-column title="商品" :width="240">
          <template #default="{ record }"><a-select v-model:value="record.productId" :options="productOptions" show-search :disabled="!!form.deliveryId" style="width:220px" /></template>
        </a-table-column>
        <a-table-column title="数量" :width="110">
          <template #default="{ record }"><a-input-number v-model:value="record.quantity" :min="0.001" :precision="3" style="width:95px" /></template>
        </a-table-column>
        <a-table-column title="售价" :width="110">
          <template #default="{ record }"><MoneyInput v-model:value="record.unitPrice" :min="0" :precision="2" :disabled="!!form.deliveryId" style="width:95px" /></template>
        </a-table-column>
        <a-table-column title="单位成本（可空）" :width="150">
          <template #default="{ record }"><MoneyInput v-model:value="record.unitCost" :min="0" :precision="2" :disabled="!!form.deliveryId" style="width:130px" placeholder="关联出库单时自动取" /></template>
        </a-table-column>
        <a-table-column title="批次" :width="100">
          <template #default="{ record }"><a-input v-model:value="record.batchNo" /></template>
        </a-table-column>
        <a-table-column title="VIN/SN" :width="240">
          <template #default="{ record }">
            <a-select v-if="form.deliveryId" v-model:value="record.serialIds" mode="multiple"
                      :options="serialOptionsForLine(record)" placeholder="选择 VIN/SN" style="width:100%" />
            <a-input v-else v-model:value="record.serialNosText" placeholder="输入 VIN/SN，逗号分隔" />
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="90">
          <template #default="{ index }"><a-button size="small" danger @click="form.lines.splice(index, 1)">删除</a-button></template>
        </a-table-column>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { salesReturnApi } from '../../api/salesreturn'
import { salesApi } from '../../api/sales'
import { customerApi, productApi, serialApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { SalesReturn, SalesReturnStatus } from '../../types/salesreturn'
import type { SalesDelivery } from '../../types/sales'
import type { ProductSerial } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')
const canPost = auth.can('IVS_STOCK_ADJUST')
const rows = ref<SalesReturn[]>([])
const customerOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const deliveryOptions = ref<{ label: string; value: string }[]>([])
const deliveries = ref<SalesDelivery[]>([])
const serials = ref<ProductSerial[]>([])
const products = ref<Record<string, string>>({})
const filterCustomer = ref<string | undefined>()
const filterStatus = ref<SalesReturnStatus | undefined>()
const formOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, any>>({ lines: [] })

const statusOptions = [
  { value: 'DRAFT', label: '草稿' }, { value: 'POSTED', label: '已过账' }, { value: 'CANCELLED', label: '已取消' },
]
const columns = [
  { title: '退货单号', key: 'returnNo', dataIndex: 'returnNo', width: 140 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 180 },
  { title: '出库单 ID', key: 'deliveryId', dataIndex: 'deliveryId', width: 230 },
  { title: '退货金额', key: 'amount', width: 130 },
  { title: '应收冲减', key: 'receivable', width: 130 },
  { title: '应退客户', key: 'refund', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 160 },
]

function statusLabel(value: string) { return statusOptions.find((item) => item.value === value)?.label || value }
function productName(id: string) { return products.value[id] || id }
function qty(value?: number) { return Number(value || 0).toFixed(3) }
function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function newLine() { return { key: 'new-' + Date.now() + '-' + Math.random(), productId: undefined, quantity: 1, unitPrice: 0, unitCost: undefined, batchNo: '-', serialIds: [] as string[], serialNosText: '' } }
function openCreate() { Object.keys(form).forEach((key) => delete form[key]); form.lines = [newLine()]; formOpen.value = true }
function addLine() { form.lines.push(newLine()) }
function serialNoOf(id: string) { return serials.value.find((item) => item.id === id)?.serialNo || id }
function serialOptionsForLine(line: Record<string, any>) {
  return (line.serialIds as string[]).map((id) => ({ label: serialNoOf(id), value: id }))
}
function onDeliveryChange() {
  const delivery = deliveries.value.find((item) => item.id === form.deliveryId)
  if (!delivery) return
  form.customerId = delivery.customerId
  form.warehouseId = delivery.warehouseId
  form.lines = delivery.lines.map((line) => ({
    key: `delivery-${delivery.id}-${line.id}`,
    productId: line.productId,
    quantity: Number(line.quantity),
    unitPrice: Number(line.unitPrice),
    unitCost: Number(line.quantity) > 0 ? Number(line.costAmount) / Number(line.quantity) : 0,
    batchNo: '-',
    serialIds: [...line.serialIds],
    serialNosText: '',
  }))
}

async function save() {
  if (!form.customerId || !form.warehouseId) { message.warning('请选择客户与退货仓库'); return }
  const lines = (form.lines as any[]).filter((line) => line.productId && Number(line.quantity) > 0)
  if (!lines.length) { message.warning('至少需要一条有效退货明细'); return }
  saving.value = true
  try {
    await salesReturnApi.create({
      customerId: form.customerId, deliveryId: form.deliveryId || null, warehouseId: form.warehouseId,
      reason: form.reason || null, remark: form.remark || null,
      lines: lines.map((line) => ({
        productId: line.productId, quantity: line.quantity, unitPrice: line.unitPrice,
        unitCost: line.unitCost == null ? null : line.unitCost, batchNo: line.batchNo || '-',
        serialNos: line.serialIds?.length ? line.serialIds.map(serialNoOf)
          : (line.serialNosText ? String(line.serialNosText).split(',').map((v: string) => v.trim()).filter(Boolean) : []),
      })),
    })
    message.success('销售退货单已保存')
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function postReturn(record: SalesReturn) {
  Modal.confirm({
    title: `确认过账 ${record.returnNo}？`, content: '过账后商品回库并冲减应收，不可撤销。',
    okText: '确认过账', cancelText: '取消',
    onOk: async () => {
      try { await salesReturnApi.post(record.id); message.success('销售退货已过账'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

function cancelReturn(record: SalesReturn) {
  Modal.confirm({
    title: `取消 ${record.returnNo}？`, okText: '确认取消', cancelText: '返回',
    onOk: async () => {
      try { await salesReturnApi.cancel(record.id); message.success('销售退货已取消'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

async function reload() {
  try { rows.value = await salesReturnApi.list({ customerId: filterCustomer.value, status: filterStatus.value }) }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [customers, warehouses, productRows, deliveryRows, serialRows] = await Promise.all([
      customerApi.list(), warehouseApi.list(), productApi.list(), salesApi.deliveries(), serialApi.list(),
    ])
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    productOptions.value = productRows.map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
    deliveries.value = deliveryRows
    deliveryOptions.value = deliveryRows.map((item) => ({ label: `${item.deliveryNo} · ${item.customerName || ''}`, value: item.id }))
    serials.value = serialRows
    products.value = Object.fromEntries(productRows.map((item) => [item.id, `${item.name}（${item.productNo}）`]))
  } catch { /* ignore */ }
  await reload()
})
</script>
