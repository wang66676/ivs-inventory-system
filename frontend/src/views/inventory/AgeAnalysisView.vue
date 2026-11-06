<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库龄按每个 VIN/SN 的实际入库时间计算；可按商品、仓库、整车/电池及库龄区间筛选。" />
      <a-row :gutter="12" style="margin-bottom:12px">
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="库存序列号" :value="filtered.length" suffix="条" /></a-card></a-col>
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="90 天以上" :value="criticalRows.length" suffix="条" /></a-card></a-col>
        <a-col :xs="24" :md="12"><a-card size="small"><a-statistic title="库存采购金额" :value="totalAmount" :precision="2" prefix="¥" /></a-card></a-col>
      </a-row>
      <a-space wrap style="margin-bottom:12px">
        <a-input v-model:value="keyword" placeholder="VIN/SN / 商品" allow-clear style="width:220px" @press-enter="reload" />
        <a-select v-model:value="productId" :options="productOptions" allow-clear show-search placeholder="按商品筛选" style="width:220px" />
        <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选" style="width:180px" />
        <a-select v-model:value="type" :options="typeOptions" allow-clear placeholder="按类型筛选" style="width:150px" />
        <a-select v-model:value="bucket" :options="bucketOptions" allow-clear placeholder="按库龄筛选" style="width:150px" />
        <a-button @click="reload">刷新</a-button>
        <a-button @click="exportRows">导出</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="filtered" :columns="columns" row-key="serialId" size="middle"
               :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 1450 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'age'"><a-tag :color="ageColor(record.ageDays)">{{ record.ageDays }} 天</a-tag></template>
          <template v-else-if="column.key === 'purchasePrice'">{{ money(record.purchasePrice) }}</template>
          <template v-else-if="column.key === 'inboundAt'">{{ formatDate(record.inboundAt) }}</template>
          <template v-else-if="column.key === 'status'">{{ record.status === 'LOCKED' ? '已锁定' : '在库' }}</template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import { productApi, warehouseApi } from '../../api/masterdata'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'
import type { AgeAnalysisItem } from '../../types/inventory'

const rows = ref<AgeAnalysisItem[]>([])
const keyword = ref('')
const productId = ref<string>()
const warehouseId = ref<string>()
const type = ref<string>()
const bucket = ref<string>()
const productOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const typeOptions = [{ value: 'VEHICLE', label: '整车' }, { value: 'BATTERY', label: '电池' }]
const bucketOptions = ['0-30 天', '31-60 天', '61-90 天', '90 天以上'].map((value) => ({ value, label: value }))
const columns = [
  { title: 'VIN / SN', key: 'serialNo', dataIndex: 'serialNo', width: 210 },
  { title: '商品名称', key: 'productName', dataIndex: 'productName', width: 210 },
  { title: '车系', key: 'series', dataIndex: 'series', width: 120 },
  { title: '类型', key: 'categoryName', dataIndex: 'categoryName', width: 110 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 140 },
  { title: '状态', key: 'status', width: 100 },
  { title: '入库时间', key: 'inboundAt', width: 170 },
  { title: '库龄', key: 'age', width: 110 },
  { title: '库龄区间', key: 'ageBucket', dataIndex: 'ageBucket', width: 120 },
  { title: '采购金额', key: 'purchasePrice', width: 130, align: 'right' },
]
const filtered = computed(() => rows.value.filter((row) => {
  const text = `${row.serialNo} ${row.productNo} ${row.productName} ${row.series || ''}`.toLowerCase()
  const matchesType = !type.value || (type.value === 'BATTERY'
    ? row.categoryCode?.toUpperCase().startsWith('BATTERY') || row.categoryName?.includes('电池')
    : !(row.categoryCode?.toUpperCase().startsWith('BATTERY') || row.categoryName?.includes('电池')))
  return (!keyword.value || text.includes(keyword.value.toLowerCase()))
    && (!productId.value || row.productId === productId.value)
    && (!warehouseId.value || row.warehouseId === warehouseId.value)
    && matchesType && (!bucket.value || row.ageBucket === bucket.value)
}))
const criticalRows = computed(() => filtered.value.filter((row) => row.ageDays > 90))
const totalAmount = computed(() => filtered.value.reduce((sum, row) => sum + Number(row.purchasePrice || 0), 0))
function money(value: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDate(value: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function ageColor(days: number) { return days > 90 ? 'red' : days > 60 ? 'orange' : days > 30 ? 'blue' : 'green' }
function reload() { inventoryApi.ageAnalysis().then((items) => { rows.value = items }).catch((error) => message.error((error as Error).message)) }
function exportRows() {
  if (!filtered.value.length) { message.warning('当前没有可导出的库龄数据'); return }
  exportCsv<AgeAnalysisItem>(`库龄分析-${exportTimestamp()}`, [
    { title: 'VIN/SN', value: (row) => row.serialNo }, { title: '商品名称', value: (row) => row.productName },
    { title: '车系', value: (row) => row.series }, { title: '类型', value: (row) => row.categoryName },
    { title: '仓库', value: (row) => row.warehouseName }, { title: '状态', value: (row) => row.status },
    { title: '入库时间', value: (row) => formatDate(row.inboundAt) }, { title: '库龄天数', value: (row) => row.ageDays },
    { title: '库龄区间', value: (row) => row.ageBucket }, { title: '采购金额', value: (row) => row.purchasePrice },
  ], filtered.value)
  message.success('库龄数据已导出')
}
onMounted(async () => {
  try {
    const [products, warehouses] = await Promise.all([productApi.list(), warehouseApi.list()])
    productOptions.value = products.map((item) => ({ label: `${item.productNo}（${item.name}）`, value: item.id }))
    warehouseOptions.value = warehouses.map((item) => ({ label: item.name, value: item.id }))
  } catch { /* ignore */ }
  reload()
})
</script>
