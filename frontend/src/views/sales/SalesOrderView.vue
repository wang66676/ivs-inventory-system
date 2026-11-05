<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px" :message="scopeHint" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建销售订单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:180px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="订单号" allow-clear style="width:190px" @press-enter="reload" />
      </a-space>
      <a-table v-resizable-columns :data-source="salesRows" :columns="columns" row-key="rowKey" size="middle"
               :pagination="false" :scroll="{ x: 1760 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'lineNo'">{{ record.line.lineNo }}</template>
          <template v-else-if="column.key === 'product'">
            <div>{{ record.line.productName }}</div>
            <a-tag v-if="record.line.belowMinPrice" color="red" style="margin-top:3px">低于限价 {{ record.line.minPrice }}</a-tag>
            <a-tag v-if="record.order.priceApprovalRequired && !record.order.priceApproved" color="red" style="margin-top:3px">需价格审批</a-tag>
          </template>
          <template v-else-if="column.key === 'batteryInfo'">{{ batteryText(record.line) }}</template>
          <template v-else-if="column.key === 'tracking'">
            <a-tag :color="record.line.trackingMode === 'SERIAL' ? 'blue' : 'default'">
              {{ record.line.trackingMode === 'SERIAL' ? 'VIN/SN' : '数量' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'quantity'">{{ record.line.quantity }}</template>
          <template v-else-if="column.key === 'deliveredQuantity'">{{ record.line.deliveredQuantity }}</template>
          <template v-else-if="column.key === 'unitPrice'">{{ money(record.line.unitPrice) }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.line.amount) }}</template>
          <template v-else-if="column.key === 'vin'">
            <a-space v-if="record.line.serialIds?.length" wrap size="small">
              <a-tag v-for="id in record.line.serialIds" :key="id">{{ serialNoOf(id) }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="record.isFirstLine" wrap>
              <a v-if="canApprovePrice && record.order.priceApprovalRequired && !record.order.priceApproved"
                 @click="approvePrice(record.order)">价格审批</a>
              <a v-if="canWrite && record.order.status === 'DRAFT'" @click="openEdit(record.order)">编辑</a>
              <a v-if="canWrite && record.order.status === 'CONFIRMED' && hasZeroPrice(record.order)"
                 @click="openPriceEditor(record.order)">补录成交价</a>
              <a v-if="canWrite && record.order.status === 'DRAFT'" @click="confirm(record.order)">确认</a>
              <a v-if="canWrite && record.order.status === 'CONFIRMED' && !record.order.lockedAt" @click="lock(record.order)">锁货</a>
              <a v-if="canWrite && record.order.lockedAt && (record.order.status === 'CONFIRMED' || record.order.status === 'PARTIALLY_DELIVERED')"
                 @click="openDeliver(record.order)">出库</a>
              <a v-if="canWrite && (record.order.status === 'DRAFT' || record.order.status === 'CONFIRMED')"
                 @click="cancel(record.order)">取消</a>
              <a v-if="canWrite && record.order.status === 'CANCELLED'" danger @click="deleteOrder(record.order)">删除</a>
            </a-space>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" :title="editingId ? '编辑销售订单' : '新建销售订单'"
             :confirm-loading="saving" ok-text="保存" cancel-text="取消" width="1120px" @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="销售订单编号"><a-input v-model:value="form.externalOrderNo" placeholder="可手工填写" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="出库仓库">
              <a-select v-model:value="form.warehouseId" :options="warehouseOptions" allow-clear
                        @change="onWarehouseChange" />
            </a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
        <a-divider orientation="left" style="font-size:13px">客户</a-divider>
        <a-radio-group v-model:value="customerMode" button-style="solid" style="margin-bottom:12px">
          <a-radio-button value="MANUAL">手工输入客户</a-radio-button>
          <a-radio-button value="EXISTING">选择已有客户</a-radio-button>
        </a-radio-group>
        <a-row v-if="customerMode === 'EXISTING'" :gutter="12">
          <a-col :span="12">
            <a-form-item label="客户" required>
              <a-select v-model:value="form.customerId" :options="customerOptions" show-search />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row v-else :gutter="12">
          <a-col :span="8"><a-form-item label="客户名称" required><a-input v-model:value="manualCustomer.name" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="联系人"><a-input v-model:value="manualCustomer.contactName" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="手机号" required><a-input v-model:value="manualCustomer.contactPhone" /></a-form-item></a-col>
        </a-row>
        <a-divider orientation="left" style="font-size:13px">订单明细（VIN/SN 可先不选，保存后点“编辑”补全）</a-divider>
        <a-table :data-source="form.lines" row-key="_key" size="small" :pagination="false" :scroll="{ x: 1900 }">
          <a-table-column title="商品" :width="280">
            <template #default="{ record: line }">
              <a-select v-model:value="line.productId" :options="productOptionsForWarehouse" show-search
                        placeholder="选择商品" option-filter-prop="label" style="width:100%"
                        :not-found-content="form.warehouseId ? '该仓库暂无可售商品' : '暂无可售商品'"
                        @change="() => onProductChange(line)" />
            </template>
          </a-table-column>
          <a-table-column title="电池箱制造商" :width="160">
            <template #default="{ record: line }"><a-input v-if="isBatteryLine(line)" v-model:value="line.batteryBoxManufacturer" placeholder="电池箱制造商" /></template>
          </a-table-column>
          <a-table-column title="电池箱型号" :width="130">
            <template #default="{ record: line }"><a-input v-if="isBatteryLine(line)" v-model:value="line.batteryBoxModel" placeholder="电池箱型号" /></template>
          </a-table-column>
          <a-table-column title="电池箱单价" :width="130">
            <template #default="{ record: line }"><MoneyInput v-if="isBatteryLine(line)" v-model:value="line.batteryBoxUnitPrice" :min="0" :precision="2" style="width:100%" /></template>
          </a-table-column>
          <a-table-column title="电池包制造商" :width="160">
            <template #default="{ record: line }"><a-input v-if="isBatteryLine(line)" v-model:value="line.batteryPackManufacturer" placeholder="电池包制造商" /></template>
          </a-table-column>
          <a-table-column title="能量（kWh）" :width="120">
            <template #default="{ record: line }"><a-input-number v-if="isBatteryLine(line)" v-model:value="line.batteryEnergyKwh" :min="0.001" :precision="3" style="width:100%" /></template>
          </a-table-column>
          <a-table-column title="电池包单价" :width="130">
            <template #default="{ record: line }"><MoneyInput v-if="isBatteryLine(line)" v-model:value="line.batteryPackUnitPrice" :min="0" :precision="2" style="width:100%" /></template>
          </a-table-column>
          <a-table-column title="数量" :width="110">
            <template #default="{ record: line }">
              <a-input-number v-model:value="line.quantity" :min="0.001" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="合计单价 / 成交价" :width="160">
            <template #default="{ record: line }">
              <span v-if="isBatteryLine(line)">{{ money(batteryUnitPrice(line)) }}</span>
              <MoneyInput v-else v-model:value="line.unitPrice" :min="0" :precision="2" placeholder="留空取指导价" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="VIN / SN" :width="320">
            <template #default="{ record: line }">
              <a-select v-if="line.trackingMode === 'SERIAL'" v-model:value="line.serialIds" mode="multiple"
                        :options="serialOptionsFor(line.productId)" placeholder="可后选，仅显示在库未锁 VIN/SN"
                        option-filter-prop="label" style="width:100%" @change="() => onSerialChange(line)" />
              <span v-else class="hint">数量商品无需 VIN</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="80">
            <template #default="{ record: line }">
              <a-button danger :disabled="form.lines.length <= 1" @click="removeLine(line._key)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>
        <a-button type="dashed" block style="margin-top:10px" @click="addLine">+ 添加一行</a-button>
      </a-form>
    </a-modal>

    <a-modal v-model:open="priceOpen" :title="`补录成交价 - ${priceOrder?.orderNo ?? ''}`"
             :confirm-loading="saving" ok-text="保存成交价" cancel-text="取消" width="1100px" @ok="savePrices">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="该订单存在 0 元成交明细，补齐所有商品成交价后才能继续出库。" />
      <a-table :data-source="priceLines" row-key="orderLineId" size="small" :pagination="false">
        <a-table-column title="商品" :width="300">
          <template #default="{ record }">{{ record.productName }}</template>
        </a-table-column>
        <a-table-column title="数量" data-index="quantity" :width="100" />
        <a-table-column title="电池箱成交价" :width="180">
          <template #default="{ record }">
            <MoneyInput v-if="record.isBattery" v-model:value="record.batteryBoxUnitPrice" :min="0" :precision="2" style="width:100%" />
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column title="电池包成交价" :width="180">
          <template #default="{ record }">
            <MoneyInput v-if="record.isBattery" v-model:value="record.batteryPackUnitPrice" :min="0" :precision="2" style="width:100%" />
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column title="成交价" :width="180">
          <template #default="{ record }">
            <span v-if="record.isBattery">{{ money(Number(record.batteryBoxUnitPrice || 0) + Number(record.batteryPackUnitPrice || 0)) }}</span>
            <MoneyInput v-else v-model:value="record.unitPrice" :min="0.01" :precision="2" style="width:100%" />
          </template>
        </a-table-column>
      </a-table>
    </a-modal>

    <a-modal v-model:open="deliverOpen" :title="`出库 - ${current?.orderNo ?? ''}`" :confirm-loading="saving"
             ok-text="确认出库" cancel-text="取消" width="900px" @ok="submitDeliver">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="订单已锁货后方可出库。序列号商品选择本次交付的 VIN/SN（可不全交）；数量商品填写本次出库数量。" />
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
                        mode="multiple" :options="serialOptionsOfLine(record)"
                        :disabled="Number(record.remainingQuantity) === 0" placeholder="选择本次交付的 VIN/SN" style="width:100%" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { salesApi } from '../../api/sales'
import { inventoryApi } from '../../api/inventory'
import { customerApi, productApi, serialApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { SALES_STATUS_COLORS, SALES_STATUS_LABELS } from '../../types/sales'
import type { ProductSerial } from '../../types/masterdata'
import type { StockBalance } from '../../types/inventory'
import type { SalesOrder, SalesOrderLine, SalesOrderStatus } from '../../types/sales'
import type { BusinessScope } from '../../utils/businessScope'

const props = withDefaults(defineProps<{ scope?: BusinessScope }>(), { scope: 'VEHICLE' })
const scopeHint = computed(() => props.scope === 'BATTERY'
  ? '电池销售独立入口：按电池 SN 匹配库存，明细填写电池箱、电池包、能量及分项成交价。'
  : '整车销售入口：车辆按 VIN 匹配库存，原有政策、锁货和出库流程保持不变。')

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')
const canApprovePrice = auth.can('IVS_PRICE_APPROVE')

const rows = ref<SalesOrder[]>([])
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const formOpen = ref(false)
const customerMode = ref<'MANUAL' | 'EXISTING'>('EXISTING')
const priceOpen = ref(false)
const deliverOpen = ref(false)
const saving = ref(false)
const editingId = ref<string>()
const current = ref<SalesOrder | null>(null)
const priceOrder = ref<SalesOrder>()
const priceLines = ref<{ orderLineId: string; productName: string; quantity: number; unitPrice?: number; isBattery: boolean; batteryBoxUnitPrice?: number; batteryPackUnitPrice?: number }[]>([])
const customerOptions = ref<{ label: string; value: string }[]>([])
const manualCustomer = reactive({ name: '', contactName: '', contactPhone: '' })
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string; name: string; trackingMode: 'QUANTITY' | 'SERIAL'; categoryCode?: string; categoryName?: string; isBattery: boolean }[]>([])
const availableSerials = ref<ProductSerial[]>([])
const stockBalances = ref<StockBalance[]>([])
interface SalesListRow {
  rowKey: string
  order: SalesOrder
  line: SalesOrderLine
  isFirstLine: boolean
}

const form = reactive<Record<string, any>>({ lines: [newLine()] })
const deliverForm = reactive<Record<string, any>>({})
const deliverInput = reactive<Record<string, { serialIds?: string[]; quantity?: number }>>({})

const salesRows = computed<SalesListRow[]>(() => rows.value.flatMap((order) => order.lines.map((line, index) => ({
  rowKey: `${order.id}-${line.id}`,
  order,
  line,
  isFirstLine: index === 0,
}))))
const columns = [
  { title: '行号', key: 'lineNo', width: 70, align: 'center' },
  { title: '商品', key: 'product', width: 240 },
  { title: '电池信息', key: 'batteryInfo', width: 300, ellipsis: true },
  { title: '追踪', key: 'tracking', width: 100, align: 'center' },
  { title: '数量', key: 'quantity', width: 90, align: 'right' },
  { title: '已出', key: 'deliveredQuantity', width: 90, align: 'right' },
  { title: '成交价', key: 'unitPrice', width: 150, align: 'right' },
  { title: '金额', key: 'amount', width: 130, align: 'right' },
  { title: 'VIN/SN', key: 'vin', width: 260 },
  { title: '操作', key: 'actions', width: 330, fixed: 'right' },
]

const productOptionsForWarehouse = computed(() => {
  const sellableIds = new Set(stockBalances.value
    .filter((row) => Number(row.availableQty) > 0)
    .filter((row) => !form.warehouseId || row.warehouseId === form.warehouseId)
    .map((row) => row.productId))
  const selectedIds = new Set((form.lines as Record<string, any>[] | undefined)
    ?.map((line) => line.productId)
    .filter(Boolean) ?? [])
  return scopedProductOptions.value
    .filter((item) => sellableIds.has(item.value)
      || (!!editingId.value && selectedIds.has(item.value)))
    .map((item) => {
      const purchaseQuantity = stockBalances.value
        .filter((row) => row.productId === item.value)
        .filter((row) => !form.warehouseId || row.warehouseId === form.warehouseId)
        .reduce((sum, row) => sum + Number(row.onHandQty || 0), 0)
      return { ...item, label: `${item.name}（采购数量：${formatQuantity(purchaseQuantity)}）` }
    })
})
const scopedProductOptions = computed(() => productOptions.value.filter((item) =>
  item.isBattery === (props.scope === 'BATTERY')))

function formatQuantity(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}

const statusOptions = Object.entries(SALES_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const statusLabel = (status: string) => SALES_STATUS_LABELS[status as SalesOrderStatus]
const statusColor = (status: string) => SALES_STATUS_COLORS[status as SalesOrderStatus]

function newLine() {
  return {
    _key: `line-${Date.now()}-${Math.random()}`,
    productId: undefined,
    quantity: 1,
    unitPrice: undefined,
    serialIds: [] as string[],
    trackingMode: 'QUANTITY' as 'QUANTITY' | 'SERIAL',
    batteryBoxManufacturer: '',
    batteryBoxModel: '',
    batteryBoxUnitPrice: undefined as number | undefined,
    batteryPackManufacturer: '',
    batteryEnergyKwh: undefined as number | undefined,
    batteryPackUnitPrice: undefined as number | undefined,
  }
}

function serialNoOf(serialId: string) {
  return availableSerials.value.find((item) => item.id === serialId)?.serialNo ?? serialId.slice(0, 8)
}
function serialOptionsFor(productId?: string) {
  return availableSerials.value
    .filter((item) => item.status === 'IN_STOCK')
    .filter((item) => !productId || item.productId === productId)
    .filter((item) => !form.warehouseId || item.warehouseId === form.warehouseId)
    .map((item) => ({ label: item.serialNo, value: item.id }))
}
function serialOptionsOfLine(line: { serialIds: string[] }) {
  const remaining = (line as unknown as SalesOrderLine).remainingSerialIds ?? line.serialIds
  return remaining.map((id) => ({ label: serialNoOf(id), value: id }))
}
function isBatteryCategory(categoryCode?: string, categoryName?: string) {
  const code = (categoryCode || '').toUpperCase()
  return code === 'BATTERY' || code.startsWith('BATTERY-') || (categoryName || '').includes('电池')
}
function isBatteryLine(line: Record<string, any>) {
  return productOptions.value.find((item) => item.value === line.productId)?.isBattery === true
}
function batteryUnitPrice(line: Record<string, any>) {
  return Number(line.batteryBoxUnitPrice || 0) + Number(line.batteryPackUnitPrice || 0)
}
function batteryText(line: Record<string, any>) {
  if (!line.batteryBoxManufacturer && !line.batteryPackManufacturer) return '-'
  return `箱：${line.batteryBoxManufacturer || '-'} / ${line.batteryBoxModel || '-'}；包：${line.batteryPackManufacturer || '-'} / ${line.batteryEnergyKwh ?? '-'}kWh`
}
function onProductChange(line: Record<string, any>) {
  const product = productOptions.value.find((item) => item.value === line.productId)
  line.trackingMode = product?.trackingMode ?? 'QUANTITY'
  line.serialIds = []
  line.batteryBoxManufacturer = ''
  line.batteryBoxModel = ''
  line.batteryBoxUnitPrice = undefined
  line.batteryPackManufacturer = ''
  line.batteryEnergyKwh = undefined
  line.batteryPackUnitPrice = undefined
}
function onWarehouseChange() {
  const allowed = new Set(productOptionsForWarehouse.value.map((item) => item.value))
  for (const line of (form.lines ?? []) as Record<string, any>[]) {
    if (line.productId && !allowed.has(line.productId)) {
      line.productId = undefined
      line.serialIds = []
      line.trackingMode = 'QUANTITY'
    }
  }
}
function onSerialChange(line: Record<string, any>) {
  if (line.trackingMode === 'SERIAL' && line.serialIds?.length) line.quantity = line.serialIds.length
}
function addLine() { form.lines.push(newLine()) }
function removeLine(key: string) {
  if (form.lines.length <= 1) return
  const index = (form.lines as Record<string, any>[]).findIndex((line) => line._key === key)
  if (index >= 0) form.lines.splice(index, 1)
}
function money(value?: number) {
  return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function allVinSelected(order: SalesOrder) {
  return order.lines.every((line) => line.trackingMode !== 'SERIAL'
    || line.serialIds.length >= Number(line.quantity))
}

function hasZeroPrice(order: SalesOrder) {
  return order.lines.some((line) => Number(line.unitPrice) <= 0)
}

async function reload() {
  try {
    const result = await salesApi.orders({ keyword: keyword.value || undefined, status: filterStatus.value })
    rows.value = result.filter((order) => (order.businessScope || 'VEHICLE') === props.scope)
  }
  catch (error) { message.error((error as Error).message) }
}
async function refreshSerials() {
  const [serials, balances] = await Promise.all([serialApi.list(), inventoryApi.balances()])
  availableSerials.value = serials
  stockBalances.value = balances
}

async function openCreate() {
  await refreshSerials()
  editingId.value = undefined
  Object.keys(form).forEach((key) => delete form[key])
  customerMode.value = 'EXISTING'
  manualCustomer.name = ''
  manualCustomer.contactName = ''
  manualCustomer.contactPhone = ''
  form.lines = [newLine()]
  formOpen.value = true
}
function openEdit(record: SalesOrder) {
  editingId.value = record.id
  Object.keys(form).forEach((key) => delete form[key])
  form.externalOrderNo = record.externalOrderNo || record.orderNo
  form.customerId = record.customerId
  customerMode.value = 'EXISTING'
  form.warehouseId = record.warehouseId
  form.remark = record.remark
  form.lines = record.lines.map((line) => ({
    _key: line.id,
    productId: line.productId,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    serialIds: [...line.serialIds],
    trackingMode: line.trackingMode,
    batteryBoxManufacturer: line.batteryBoxManufacturer || '',
    batteryBoxModel: line.batteryBoxModel || '',
    batteryBoxUnitPrice: line.batteryBoxUnitPrice,
    batteryPackManufacturer: line.batteryPackManufacturer || '',
    batteryEnergyKwh: line.batteryEnergyKwh,
    batteryPackUnitPrice: line.batteryPackUnitPrice,
  }))
  formOpen.value = true
}

function openPriceEditor(record: SalesOrder) {
  priceOrder.value = record
  priceLines.value = record.lines.map((line) => ({
    orderLineId: line.id,
    productName: line.productName,
    quantity: Number(line.quantity),
    unitPrice: Number(line.unitPrice) > 0 ? Number(line.unitPrice) : undefined,
    isBattery: isBatteryLine(line as unknown as Record<string, any>),
    batteryBoxUnitPrice: line.batteryBoxUnitPrice,
    batteryPackUnitPrice: line.batteryPackUnitPrice,
  }))
  priceOpen.value = true
}

async function savePrices() {
  if (!priceOrder.value) return
  for (const line of priceLines.value) {
    if (line.isBattery) {
      if (Number(line.batteryBoxUnitPrice || 0) < 0 || Number(line.batteryPackUnitPrice || 0) < 0
          || Number(line.batteryBoxUnitPrice || 0) + Number(line.batteryPackUnitPrice || 0) <= 0) {
        message.warning('请填写有效的电池箱和电池包成交价')
        return
      }
    } else if (!line.unitPrice || Number(line.unitPrice) <= 0) {
      message.warning('请为所有商品填写大于 0 的成交价')
      return
    }
  }
  saving.value = true
  try {
    await salesApi.updatePrices(priceOrder.value.id, priceLines.value.map((line) => ({
      orderLineId: line.orderLineId,
      unitPrice: line.isBattery
        ? Number(line.batteryBoxUnitPrice || 0) + Number(line.batteryPackUnitPrice || 0)
        : Number(line.unitPrice),
      batteryBoxUnitPrice: line.isBattery ? Number(line.batteryBoxUnitPrice || 0) : null,
      batteryPackUnitPrice: line.isBattery ? Number(line.batteryPackUnitPrice || 0) : null,
    })))
    message.success('成交价已补录，可以继续出库')
    priceOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (customerMode.value === 'MANUAL') {
    if (!manualCustomer.name.trim() || !manualCustomer.contactPhone.trim()) {
      message.warning('请填写客户名称和手机号')
      return
    }
  } else if (!form.customerId) {
    message.warning('请选择客户')
    return
  }
  const lines = (form.lines as Record<string, any>[]).filter((line) => line.productId && Number(line.quantity) > 0)
  if (!lines.length) { message.warning('请至少填写一行有效明细'); return }
  for (const line of lines) {
    if (!isBatteryLine(line)) continue
    if (!String(line.batteryBoxManufacturer || '').trim() || !String(line.batteryBoxModel || '').trim()
        || !String(line.batteryPackManufacturer || '').trim()) {
      message.warning('请填写电池箱制造商、电池箱型号和电池包制造商')
      return
    }
    if (!(Number(line.batteryEnergyKwh) > 0)) { message.warning('电池能量（kWh）必须大于 0'); return }
    if (Number(line.batteryBoxUnitPrice || 0) < 0 || Number(line.batteryPackUnitPrice || 0) < 0
        || batteryUnitPrice(line) <= 0) {
      message.warning('请填写有效的电池箱和电池包成交价')
      return
    }
  }
  const payload = {
    externalOrderNo: form.externalOrderNo || null,
    customerId: form.customerId,
    warehouseId: form.warehouseId,
    remark: form.remark,
    lines: lines.map((line) => ({
      productId: line.productId,
      quantity: line.quantity,
      unitPrice: isBatteryLine(line) ? batteryUnitPrice(line) : (line.unitPrice ?? null),
      serialIds: line.serialIds?.length ? line.serialIds : null,
      batteryBoxManufacturer: isBatteryLine(line) ? line.batteryBoxManufacturer : null,
      batteryBoxModel: isBatteryLine(line) ? line.batteryBoxModel : null,
      batteryBoxUnitPrice: isBatteryLine(line) ? line.batteryBoxUnitPrice : null,
      batteryPackManufacturer: isBatteryLine(line) ? line.batteryPackManufacturer : null,
      batteryEnergyKwh: isBatteryLine(line) ? line.batteryEnergyKwh : null,
      batteryPackUnitPrice: isBatteryLine(line) ? line.batteryPackUnitPrice : null,
    })),
  }
  saving.value = true
  try {
    if (customerMode.value === 'MANUAL') {
      const createdCustomer = await customerApi.create({
        name: manualCustomer.name.trim(),
        customerType: 'INDIVIDUAL',
        contactName: manualCustomer.contactName.trim() || manualCustomer.name.trim(),
        contactPhone: manualCustomer.contactPhone.trim(),
        creditLimit: 0,
      })
      form.customerId = createdCustomer.id
      payload.customerId = createdCustomer.id
      customerOptions.value.unshift({
        label: `${createdCustomer.name}（${createdCustomer.code}）`, value: createdCustomer.id,
      })
    }
    if (editingId.value) await salesApi.update(editingId.value, payload)
    else await salesApi.create(payload)
    message.success(editingId.value ? '销售订单已更新' : '销售订单已创建')
    formOpen.value = false
    await Promise.all([reload(), refreshSerials()])
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function approvePrice(record: SalesOrder) {
  try { await salesApi.approvePrice(record.id); message.success('价格审批完成'); await reload() }
  catch (error) { message.error((error as Error).message) }
}
async function confirm(record: SalesOrder) {
  if (!allVinSelected(record)) { message.warning('请先点“编辑”补全所有 VIN，再确认订单'); return }
  try { await salesApi.confirm(record.id); message.success('订单已确认'); await reload() }
  catch (error) { message.error((error as Error).message) }
}
async function lock(record: SalesOrder) {
  if (!allVinSelected(record)) { message.warning('所有序列号商品都选定 VIN 后才能锁货'); return }
  try {
    await salesApi.lock(record.id)
    message.success('已锁货')
    await Promise.all([reload(), refreshSerials()])
  } catch (error) { message.error((error as Error).message) }
}
async function cancel(record: SalesOrder) {
  try { await salesApi.cancel(record.id); message.success('订单已取消'); await Promise.all([reload(), refreshSerials()]) }
  catch (error) { message.error((error as Error).message) }
}
function deleteOrder(record: SalesOrder) {
  Modal.confirm({
    title: `删除已取消订单 ${record.orderNo}？`,
    content: '删除后订单及其明细不可恢复。',
    okText: '确认删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try { await salesApi.deleteOrder(record.id); message.success('订单已删除'); await reload() }
      catch (error) { message.error((error as Error).message) }
    },
  })
}

function openDeliver(record: SalesOrder) {
  if (!record.lockedAt) { message.warning('请先锁货再出库'); return }
  current.value = record
  Object.keys(deliverForm).forEach((key) => delete deliverForm[key])
  Object.keys(deliverInput).forEach((key) => delete deliverInput[key])
  deliverForm.warehouseId = record.warehouseId
  record.lines.forEach((line) => { deliverInput[line.id] = {} })
  deliverOpen.value = true
}
async function submitDeliver() {
  if (!current.value) return
  if (!current.value.lockedAt) { message.warning('未锁货订单不能出库'); return }
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
    const delivery = await salesApi.deliver(current.value.id, { warehouseId: deliverForm.warehouseId, lines })
    message.success(`出库完成：${delivery.deliveryNo}，金额 ${money(delivery.amount)}，毛利 ${money(delivery.grossProfit)}`)
    deliverOpen.value = false
    await Promise.all([reload(), refreshSerials()])
  } catch (error) { message.error((error as Error).message) }
  finally { saving.value = false }
}

onMounted(async () => {
  try {
    const [customers, warehouses, products, serials, balances] = await Promise.all([
      customerApi.list(), warehouseApi.list(), productApi.list(), serialApi.list(), inventoryApi.balances(),
    ])
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    productOptions.value = products.map((item) => ({
      label: item.name,
      value: item.id,
      name: item.name,
      trackingMode: item.trackingMode,
      categoryCode: item.categoryCode,
      categoryName: item.categoryName,
      isBattery: isBatteryCategory(item.categoryCode, item.categoryName),
    }))
    availableSerials.value = serials
    stockBalances.value = balances
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>
