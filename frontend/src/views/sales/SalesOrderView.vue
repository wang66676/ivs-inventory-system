<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="销售订单：整车按 VIN 逐台销售，单价可手工录入；未填单价时回落到商品指导价。低于最低限价会自动标记「需价格审批」，未审批不能确认。确认后锁货，出库即扣库存、结转成本、生成应收。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建销售订单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:180px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="订单号" allow-clear style="width:190px" @press-enter="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="false">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="行号" data-index="lineNo" :width="70" />
            <a-table-column title="商品" :width="230">
              <template #default="{ record: line }">{{ line.productName }}（{{ line.productNo }}）</template>
            </a-table-column>
            <a-table-column title="追踪" :width="100">
              <template #default="{ record: line }">
                <a-tag :color="line.trackingMode === 'SERIAL' ? 'blue' : 'default'">
                  {{ line.trackingMode === 'SERIAL' ? '序列号' : '数量' }}
                </a-tag>
              </template>
            </a-table-column>
            <a-table-column title="数量" data-index="quantity" :width="100" />
            <a-table-column title="已出" data-index="deliveredQuantity" :width="100" />
            <a-table-column title="成交价" :width="140">
              <template #default="{ record: line }">
                {{ line.unitPrice }}
                <a-tag v-if="line.belowMinPrice" color="red" style="margin-left:4px">低于限价 {{ line.minPrice }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="金额" data-index="amount" :width="130" />
            <a-table-column title="序列号" :width="220">
              <template #default="{ record: line }">
                <a-tag v-for="id in line.serialIds" :key="id">{{ serialNoOf(id) }}</a-tag>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            <a-tag v-if="record.priceApprovalRequired && !record.priceApproved" color="red">需价格审批</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space wrap>
              <a v-if="canApprovePrice && record.priceApprovalRequired && !record.priceApproved"
                 @click="approvePrice(record)">价格审批</a>
              <a v-if="canWrite && record.status === 'DRAFT'" @click="confirm(record)">确认</a>
              <a v-if="canWrite && record.status === 'CONFIRMED'" @click="lock(record)">锁货</a>
              <a v-if="canWrite && (record.status === 'CONFIRMED' || record.status === 'PARTIALLY_DELIVERED')"
                 @click="openDeliver(record)">出库</a>
              <a v-if="canWrite && (record.status === 'DRAFT' || record.status === 'CONFIRMED')"
                 @click="cancel(record)">取消</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="createOpen" title="新建销售订单" :confirm-loading="saving" ok-text="提交" cancel-text="取消"
             width="960px" @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="客户" required><a-select v-model:value="form.customerId" :options="customerOptions" show-search /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="出库仓库"><a-select v-model:value="form.warehouseId" :options="warehouseOptions" allow-clear /></a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
        <a-divider orientation="left" style="font-size:13px">订单明细（整车按 VIN，单价可手工录入）</a-divider>
        <div v-for="(line, index) in form.lines" :key="index" class="line-row">
          <a-space wrap>
            <a-select v-model:value="line.productId" :options="productOptions" show-search placeholder="商品"
                      style="width:280px" @change="() => onProductChange(line)" />
            <a-input-number v-model:value="line.quantity" :min="0.001" placeholder="数量" style="width:110px" />
            <a-input-number v-model:value="line.unitPrice" :min="0" placeholder="单价" style="width:130px" />
            <a-select v-if="line.trackingMode === 'SERIAL'" v-model:value="line.serialIds" mode="multiple"
                      :options="serialOptionsFor(line.productId)" placeholder="选择 VIN / 电池 SN" style="width:300px" />
            <a-button danger :disabled="form.lines.length <= 1" @click="form.lines.splice(index, 1)">删除</a-button>
          </a-space>
        </div>
        <a-button type="dashed" block @click="addLine">+ 添加一行</a-button>
      </a-form>
    </a-modal>

    <a-modal v-model:open="deliverOpen" :title="`出库 - ${current?.orderNo ?? ''}`" :confirm-loading="saving"
             ok-text="确认出库" cancel-text="取消" width="900px" @ok="submitDeliver">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="序列号商品选择本次交付的 VIN（可不全交）；数量商品填写本次出库数量。出库后扣减库存、结转成本并生成应收。" />
      <a-form layout="vertical">
        <a-form-item label="出库仓库" required><a-select v-model:value="deliverForm.warehouseId" :options="warehouseOptions" /></a-form-item>
        <a-table :data-source="current?.lines ?? []" row-key="id" size="small" :pagination="false">
          <a-table-column title="商品" :width="200">
            <template #default="{ record }">{{ record.productName }}</template>
          </a-table-column>
          <a-table-column title="待出" data-index="remainingQuantity" :width="90" />
          <a-table-column title="本次出库" :width="420">
            <template #default="{ record }">
              <a-select v-if="record.trackingMode === 'SERIAL'" v-model:value="deliverInput[record.id].serialIds"
                        mode="multiple" :options="serialOptionsOfLine(record)" :disabled="Number(record.remainingQuantity) === 0"
                        placeholder="选择本次交付的 VIN" style="width:100%" />
              <a-input-number v-else v-model:value="deliverInput[record.id].quantity" :min="0"
                              :max="Number(record.remainingQuantity)" :disabled="Number(record.remainingQuantity) === 0"
                              style="width:160px" />
            </template>
          </a-table-column>
        </a-table>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { salesApi } from '../../api/sales'
import { customerApi, productApi, serialApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { SALES_STATUS_COLORS, SALES_STATUS_LABELS } from '../../types/sales'
import type { ProductSerial } from '../../types/masterdata'
import type { SalesOrder, SalesOrderStatus } from '../../types/sales'

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')
const canApprovePrice = auth.can('IVS_PRICE_APPROVE')

const rows = ref<SalesOrder[]>([])
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const createOpen = ref(false)
const deliverOpen = ref(false)
const saving = ref(false)
const current = ref<SalesOrder | null>(null)
const customerOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const availableSerials = ref<ProductSerial[]>([])

const form = reactive<Record<string, any>>({ lines: [newLine()] })
const deliverForm = reactive<Record<string, any>>({})
const deliverInput = reactive<Record<string, { serialIds?: string[]; quantity?: number }>>({})

const columns = [
  { title: '订单号', key: 'orderNo', dataIndex: 'orderNo', width: 150 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 220 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '金额', key: 'totalAmount', dataIndex: 'totalAmount', width: 140 },
  { title: '状态', key: 'status', width: 220 },
  { title: '操作', key: 'actions', width: 260 },
]

const statusOptions = Object.entries(SALES_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const statusLabel = (status: string) => SALES_STATUS_LABELS[status as SalesOrderStatus]
const statusColor = (status: string) => SALES_STATUS_COLORS[status as SalesOrderStatus]

function newLine() {
  return { productId: undefined, quantity: 1, unitPrice: undefined, serialIds: [], trackingMode: 'QUANTITY' }
}

function serialNoOf(serialId: string) {
  return availableSerials.value.find((item) => item.id === serialId)?.serialNo ?? serialId.slice(0, 8)
}

function serialOptionsFor(productId?: string) {
  return availableSerials.value
    .filter((item) => !productId || item.productId === productId)
    .map((item) => ({ label: `${item.serialNo}`, value: item.id }))
}

function serialOptionsOfLine(line: { serialIds: string[] }) {
  return line.serialIds.map((id) => ({ label: serialNoOf(id), value: id }))
}

function onProductChange(line: Record<string, any>) {
  const product = productOptions.value.find((item) => item.value === line.productId)
  line.trackingMode = product && String(product.label).includes('序列号') ? 'SERIAL' : 'QUANTITY'
  line.serialIds = []
}

function addLine() {
  form.lines.push(newLine())
}

async function reload() {
  try {
    rows.value = await salesApi.orders({ keyword: keyword.value || undefined, status: filterStatus.value })
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  form.lines = [newLine()]
  createOpen.value = true
}

async function submit() {
  if (!form.customerId) { message.warning('请选择客户'); return }
  const lines = (form.lines as any[]).filter((line) => line.productId && line.quantity > 0)
  if (!lines.length) { message.warning('请至少填写一行有效明细'); return }
  saving.value = true
  try {
    await salesApi.create({
      customerId: form.customerId,
      warehouseId: form.warehouseId,
      remark: form.remark,
      lines: lines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        unitPrice: line.unitPrice ?? null,
        serialIds: line.serialIds?.length ? line.serialIds : null,
      })),
    })
    message.success('销售订单已创建')
    createOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function approvePrice(record: SalesOrder) {
  try {
    await salesApi.approvePrice(record.id)
    message.success('价格审批完成')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function confirm(record: SalesOrder) {
  try {
    await salesApi.confirm(record.id)
    message.success('订单已确认')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function lock(record: SalesOrder) {
  try {
    await salesApi.lock(record.id)
    message.success('已锁货')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function cancel(record: SalesOrder) {
  try {
    await salesApi.cancel(record.id)
    message.success('订单已取消')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openDeliver(record: SalesOrder) {
  current.value = record
  Object.keys(deliverForm).forEach((key) => delete deliverForm[key])
  Object.keys(deliverInput).forEach((key) => delete deliverInput[key])
  deliverForm.warehouseId = record.warehouseId
  record.lines.forEach((line) => { deliverInput[line.id] = {} })
  deliverOpen.value = true
}

async function submitDeliver() {
  if (!current.value) return
  if (!deliverForm.warehouseId) { message.warning('请选择出库仓库'); return }
  const lines: Record<string, unknown>[] = []
  for (const line of current.value.lines) {
    if (Number(line.remainingQuantity) === 0) continue
    const input = deliverInput[line.id] ?? {}
    if (line.trackingMode === 'SERIAL') {
      if (input.serialIds?.length) lines.push({ orderLineId: line.id, serialIds: input.serialIds })
    } else if (input.quantity && Number(input.quantity) > 0) {
      lines.push({ orderLineId: line.id, quantity: input.quantity })
    }
  }
  if (!lines.length) { message.warning('请填写本次出库内容'); return }
  saving.value = true
  try {
    const delivery = await salesApi.deliver(current.value.id,
        { warehouseId: deliverForm.warehouseId, lines })
    message.success(`出库完成：${delivery.deliveryNo}，金额 ${delivery.amount}，毛利 ${delivery.grossProfit}`)
    deliverOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const [customers, warehouses, products, serials] = await Promise.all([
      customerApi.list(), warehouseApi.list(), productApi.list(), serialApi.list({ status: 'IN_STOCK' }),
    ])
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    productOptions.value = products.map((item) => ({
      label: `${item.name}（${item.productNo}）${item.trackingMode === 'SERIAL' ? ' [序列号]' : ''}`,
      value: item.id,
    }))
    availableSerials.value = serials
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.line-row { margin-bottom: 8px; }
</style>
