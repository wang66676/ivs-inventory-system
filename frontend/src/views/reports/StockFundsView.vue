<template>
  <div>
    <a-card :bordered="false">
      <a-space wrap style="margin-bottom:16px">
        <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear show-search
                  placeholder="按仓库筛选" style="width:240px" @change="reload" />
        <a-button @click="reload">刷新</a-button>
        <a-button type="primary" @click="exportCsv">导出 CSV</a-button>
      </a-space>
      <a-row :gutter="16" style="margin-bottom:16px">
        <a-col :span="8"><a-card size="small"><a-statistic title="库存资金占用" :value="report?.totalAmount || 0" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="8"><a-card size="small"><a-statistic title="在库数量" :value="report?.totalOnHandQty || 0" :precision="3" /></a-card></a-col>
        <a-col :span="8"><a-card size="small"><a-statistic title="库存明细行" :value="report?.rows?.length || 0" /></a-card></a-col>
      </a-row>
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库存资金按当前库存余额金额汇总；库龄按该商品/仓库最早入库台账日期计算，用于识别长期占用资金。" />
      <a-table :data-source="report?.rows || []" :columns="columns" :row-key="rowKey" size="middle"
               :pagination="{ pageSize: 20 }" :scroll="{ x: 1300 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'product'">{{ record.productName }}（{{ record.productNo }}）</template>
          <template v-else-if="column.key === 'qty'">{{ Number(record.onHandQty).toFixed(3) }}</template>
          <template v-else-if="column.key === 'locked'">{{ Number(record.lockedQty).toFixed(3) }}</template>
          <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
          <template v-else-if="column.key === 'share'"><a-progress :percent="Math.min(100, Number(record.share))" size="small" /></template>
          <template v-else-if="column.key === 'age'">
            <a-tag :color="Number(record.ageDays) > 90 ? 'red' : Number(record.ageDays) > 60 ? 'orange' : 'green'">
              {{ record.ageDays }} 天
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { reportApi } from '../../api/report'
import { warehouseApi } from '../../api/masterdata'
import type { StockFundReport, StockFundRow } from '../../types/report'

const report = ref<StockFundReport>()
const warehouseId = ref<string | undefined>()
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const columns = [
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 150 },
  { title: '商品', key: 'product', width: 250 },
  { title: '产品大项', key: 'categoryName', dataIndex: 'categoryName', width: 130 },
  { title: '在库数量', key: 'qty', width: 110 },
  { title: '锁定数量', key: 'locked', width: 110 },
  { title: '资金占用', key: 'amount', width: 140 },
  { title: '占比', key: 'share', width: 150 },
  { title: '库龄', key: 'age', width: 100 },
]

function rowKey(row: StockFundRow) { return row.warehouseId + '|' + row.productId }
function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }

async function reload() {
  try { report.value = await reportApi.stockFunds({ warehouseId: warehouseId.value }) }
  catch (error) { message.error((error as Error).message) }
}

function exportCsv() {
  const rows = report.value?.rows || []
  const header = ['仓库','商品编码','商品名称','产品大项','在库数量','锁定数量','资金占用','占比%','库龄天数']
  const lines = rows.map((r) => [r.warehouseName, r.productNo, r.productName, r.categoryName,
    r.onHandQty, r.lockedQty, r.amount, r.share, r.ageDays])
  const csv = [header, ...lines].map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '库存资金占用.csv'
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(async () => {
  try {
    const warehouses = await warehouseApi.list()
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>
