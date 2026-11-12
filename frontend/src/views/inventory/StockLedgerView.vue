<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库存台账：只追加的事实表。每一笔变动都记录方向、数量、单位成本、金额与变动后余额，并关联业务单据（收货单号等）。台账可完整重建余额。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="productSeries" :options="productOptions" allow-clear show-search placeholder="按车系筛选"
                  style="width:280px" @change="reload" />
        <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选"
                  style="width:190px" @change="reload" />
        <a-select v-model:value="moveType" :options="moveTypeOptions" allow-clear placeholder="按类型筛选"
                  style="width:170px" @change="reload" />
        <a-input-number v-model:value="limit" :min="10" :max="500" style="width:120px" @change="reload" />
        <a-button @click="exportLedger">导出</a-button>
        <span class="hint">共 {{ rows.length }} 条</span>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 1200 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'direction'">
            <a-tag :color="record.direction === 'IN' ? 'green' : 'red'">
              {{ record.direction === 'IN' ? '入库' : '出库' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'moveType'">
            {{ MOVE_TYPE_LABELS[record.moveType] ?? record.moveType }}
          </template>
          <template v-else-if="column.key === 'purchaseOrderNo'">
            {{ record.purchaseOrderNo || '-' }}
          </template>
          <template v-else-if="column.key === 'serialNo'">
            {{ record.serialNo ?? '-' }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import { productApi, warehouseApi } from '../../api/masterdata'
import { MOVE_TYPE_LABELS } from '../../types/inventory'
import type { LedgerEntry } from '../../types/inventory'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'
import type { BusinessScope } from '../../utils/businessScope'
import { isBatteryCategory } from '../../utils/businessScope'

const props = withDefaults(defineProps<{ scope?: BusinessScope }>(), { scope: 'VEHICLE' })

const rows = ref<LedgerEntry[]>([])
const productSeries = ref<string | undefined>(undefined)
const warehouseId = ref<string | undefined>(undefined)
const moveType = ref<string | undefined>(undefined)
const limit = ref(500)
const productOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const scopedProductIds = ref<Set<string>>(new Set())
const moveTypeOptions = Object.entries(MOVE_TYPE_LABELS).map(([value, label]) => ({ value, label }))

const columns = [
  { title: '采购订单编号', key: 'purchaseOrderNo', width: 170 },
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 200 },
  { title: '序列号', key: 'serialNo', width: 190 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '方向', key: 'direction', width: 90 },
  { title: '类型', key: 'moveType', width: 120 },
  { title: '数量', key: 'quantity', dataIndex: 'quantity', width: 100 },
  { title: '单位成本', key: 'unitCost', dataIndex: 'unitCost', width: 120 },
  { title: '金额', key: 'amount', dataIndex: 'amount', width: 130 },
  { title: '变动后余额', key: 'balanceAfter', dataIndex: 'balanceAfter', width: 120 },
  { title: '业务单据', key: 'bizNo', dataIndex: 'bizNo', width: 140 },
]

async function reload() {
  try {
    const result = await inventoryApi.ledger({
      warehouseId: warehouseId.value, moveType: moveType.value, limit: limit.value,
    })
    rows.value = result.filter((row) => scopedProductIds.value.has(row.productId)
      && (!productSeries.value || row.series === productSeries.value))
  } catch (error) {
    message.error((error as Error).message)
  }
}

function exportLedger() {
  if (!rows.value.length) { message.warning('当前没有可导出的库存台账数据'); return }
  exportCsv<LedgerEntry>(`库存台账-${exportTimestamp()}`, [
    { title: '采购订单编号', value: (row) => row.purchaseOrderNo },
    { title: '商品编码', value: (row) => row.productNo },
    { title: '商品名称', value: (row) => row.productName },
    { title: 'VIN/SN', value: (row) => row.serialNo },
    { title: '仓库', value: (row) => row.warehouseName },
    { title: '方向', value: (row) => row.direction === 'IN' ? '入库' : '出库' },
    { title: '类型', value: (row) => MOVE_TYPE_LABELS[row.moveType] ?? row.moveType },
    { title: '数量', value: (row) => row.quantity },
    { title: '单位成本', value: (row) => row.unitCost },
    { title: '金额', value: (row) => row.amount },
    { title: '变动后余额', value: (row) => row.balanceAfter },
    { title: '业务单据', value: (row) => row.bizNo },
    { title: '备注', value: (row) => row.remark },
  ], rows.value)
  message.success('库存台账已导出')
}

onMounted(async () => {
  try {
    const [products, warehouses] = await Promise.all([productApi.list(), warehouseApi.list()])
    const scopedProducts = products.filter((item) => isBatteryCategory(item.categoryCode, item.categoryName) === (props.scope === 'BATTERY'))
    scopedProductIds.value = new Set(scopedProducts.map((item) => item.id))
    const series = [...new Set(scopedProducts.map((item) => item.series || item.name).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    productOptions.value = series.map((item) => ({ label: item, value: item }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>
