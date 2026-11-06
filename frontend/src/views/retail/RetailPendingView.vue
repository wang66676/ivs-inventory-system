<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="已交付车辆如果未完成实销，会直接影响政策返利申报；超过 30 天提醒，超过 90 天标红预警。" />
      <a-row :gutter="12" style="margin-bottom:12px">
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="待实销车辆" :value="filtered.length" suffix="台" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="超过 30 天" :value="warningRows.length" suffix="台" /></a-card></a-col>
        <a-col :xs="24" :md="8"><a-card size="small"><a-statistic title="超过 90 天" :value="criticalRows.length" suffix="台" /></a-card></a-col>
      </a-row>
      <a-space wrap style="margin-bottom:12px">
        <a-input v-model:value="keyword" placeholder="VIN / 商品 / 订单 / 客户" allow-clear style="width:250px" />
        <a-select v-model:value="level" :options="levelOptions" allow-clear placeholder="预警级别" style="width:160px" />
        <a-button @click="reload">刷新</a-button>
        <a-button @click="exportRows">导出</a-button>
        <a-button type="primary" @click="goRetail">去登记实销</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="filtered" :columns="columns" row-key="serial.id" size="middle"
               :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 1700 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'serialNo'">{{ record.serial.serialNo }}</template>
          <template v-else-if="column.key === 'product'">{{ record.serial.productName || '-' }}</template>
          <template v-else-if="column.key === 'deliveredAt'">{{ formatDate(record.deliveredAt) }}</template>
          <template v-else-if="column.key === 'pendingDays'">
            <a-tag :color="record.pendingDays > 90 ? 'red' : record.pendingDays > 30 ? 'orange' : 'blue'">
              {{ record.pendingDays }} 天
            </a-tag>
          </template>
          <template v-else-if="column.key === 'level'">{{ levelLabel(record.pendingDays) }}</template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { retailApi } from '../../api/retail'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'
import type { RetailPendingItem } from '../../types/retail'

const router = useRouter()
const rows = ref<RetailPendingItem[]>([])
const keyword = ref('')
const level = ref<string>()
const levelOptions = [
  { value: 'NORMAL', label: '30 天以内' },
  { value: 'WARNING', label: '超过 30 天' },
  { value: 'CRITICAL', label: '超过 90 天' },
]
const columns = [
  { title: 'VIN', key: 'serialNo', width: 200 },
  { title: '商品', key: 'product', width: 190 },
  { title: '车系', dataIndex: 'series', key: 'series', width: 110 },
  { title: '客户', dataIndex: 'customerName', key: 'customerName', width: 190 },
  { title: '销售订单', dataIndex: 'orderNo', key: 'orderNo', width: 150 },
  { title: '出库单', dataIndex: 'deliveryNo', key: 'deliveryNo', width: 150 },
  { title: '交付时间', key: 'deliveredAt', width: 120 },
  { title: '待实销天数', key: 'pendingDays', width: 120 },
  { title: '预警级别', key: 'level', width: 110 },
  { title: '采购订单', dataIndex: 'purchaseOrderNo', key: 'purchaseOrderNo', width: 150 },
]
const filtered = computed(() => rows.value.filter((row) => {
  const text = `${row.serial.serialNo} ${row.serial.productName || ''} ${row.orderNo} ${row.customerName}`.toLowerCase()
  const matchesLevel = !level.value
    || (level.value === 'NORMAL' && row.pendingDays <= 30)
    || (level.value === 'WARNING' && row.pendingDays > 30 && row.pendingDays <= 90)
    || (level.value === 'CRITICAL' && row.pendingDays > 90)
  return (!keyword.value || text.includes(keyword.value.toLowerCase())) && matchesLevel
}))
const warningRows = computed(() => filtered.value.filter((row) => row.pendingDays > 30))
const criticalRows = computed(() => filtered.value.filter((row) => row.pendingDays > 90))
function formatDate(value: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function levelLabel(days: number) { return days > 90 ? '严重' : days > 30 ? '警告' : '正常' }
function reload() { retailApi.pending().then((items) => { rows.value = items }).catch((error) => message.error((error as Error).message)) }
function goRetail() { router.push('/retail/records') }
function exportRows() {
  if (!filtered.value.length) { message.warning('当前没有待实销数据'); return }
  exportCsv<RetailPendingItem>(`未实销预警-${exportTimestamp()}`, [
    { title: 'VIN', value: (row) => row.serial.serialNo }, { title: '商品', value: (row) => row.serial.productName },
    { title: '车系', value: (row) => row.series }, { title: '客户', value: (row) => row.customerName },
    { title: '销售订单', value: (row) => row.orderNo }, { title: '出库单', value: (row) => row.deliveryNo },
    { title: '交付时间', value: (row) => formatDate(row.deliveredAt) }, { title: '待实销天数', value: (row) => row.pendingDays },
    { title: '预警级别', value: (row) => levelLabel(row.pendingDays) }, { title: '采购订单', value: (row) => row.purchaseOrderNo },
  ], filtered.value)
  message.success('未实销预警已导出')
}
onMounted(reload)
</script>
