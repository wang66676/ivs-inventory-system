<template>
  <div>
    <a-card :bordered="false">
      <a-space wrap style="margin-bottom:16px">
        <a-range-picker v-model:value="range" :allow-clear="false" @change="reload" />
        <a-button @click="reload">查询</a-button>
        <a-button type="primary" @click="exportCsv">导出 CSV</a-button>
      </a-space>
      <a-row :gutter="16" style="margin-bottom:16px">
        <a-col :span="6"><a-card size="small"><a-statistic title="销售收入" :value="totals.sales" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="6"><a-card size="small"><a-statistic title="销售成本" :value="totals.cost" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="6"><a-card size="small"><a-statistic title="毛利" :value="totals.gross" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="6"><a-card size="small"><a-statistic title="政策后净利" :value="totals.netProfit" :precision="2" prefix="¥" /></a-card></a-col>
      </a-row>
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="毛利按商品汇总销售出库与销售退货，成本来自库存结转；政策后净利扣除本期间政策返利成本。" />
      <a-table :data-source="rows" :columns="columns" row-key="productId" size="middle"
               :pagination="{ pageSize: 20 }" :scroll="{ x: 1700 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'product'">{{ record.productName }}（{{ record.productNo }}）</template>
          <template v-else-if="column.key === 'qty'">{{ Number(record.salesQuantity).toFixed(3) }}</template>
          <template v-else-if="column.key === 'sales'">{{ money(record.salesAmount) }}</template>
          <template v-else-if="column.key === 'return'">{{ money(record.salesReturnAmount) }}</template>
          <template v-else-if="column.key === 'netSales'">{{ money(record.netSalesAmount) }}</template>
          <template v-else-if="column.key === 'cost'">{{ money(record.netCostAmount) }}</template>
          <template v-else-if="column.key === 'gross'">{{ money(record.grossProfit) }}</template>
          <template v-else-if="column.key === 'margin'">
            <a-tag :color="Number(record.grossMargin) >= 20 ? 'green' : Number(record.grossMargin) >= 10 ? 'orange' : 'red'">
              {{ Number(record.grossMargin).toFixed(2) }}%
            </a-tag>
          </template>
          <template v-else-if="column.key === 'rebate'">{{ money(record.rebateCost) }}</template>
          <template v-else-if="column.key === 'netProfit'">{{ money(record.netProfit) }}</template>
          <template v-else-if="column.key === 'netMargin'">{{ Number(record.netMargin).toFixed(2) }}%</template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { reportApi } from '../../api/report'
import type { GrossProfitRow } from '../../types/report'

const rows = ref<GrossProfitRow[]>([])
const range = ref<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()])
const columns = [
  { title: '商品', key: 'product', width: 240, fixed: 'left' },
  { title: '销售数量', key: 'qty', width: 110 },
  { title: '销售金额', key: 'sales', width: 130 },
  { title: '退货金额', key: 'return', width: 130 },
  { title: '净销售', key: 'netSales', width: 130 },
  { title: '净成本', key: 'cost', width: 130 },
  { title: '毛利', key: 'gross', width: 130 },
  { title: '毛利率', key: 'margin', width: 100 },
  { title: '政策返利成本', key: 'rebate', width: 150 },
  { title: '政策后净利', key: 'netProfit', width: 140 },
  { title: '净利率', key: 'netMargin', width: 100 },
]
const totals = computed(() => rows.value.reduce((acc, row) => ({
  sales: acc.sales + Number(row.netSalesAmount),
  cost: acc.cost + Number(row.netCostAmount),
  gross: acc.gross + Number(row.grossProfit),
  netProfit: acc.netProfit + Number(row.netProfit),
}), { sales: 0, cost: 0, gross: 0, netProfit: 0 }))

function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }

async function reload() {
  try {
    const report = await reportApi.grossProfit({
      from: range.value[0].format('YYYY-MM-DD'), to: range.value[1].format('YYYY-MM-DD'),
    })
    rows.value = report.rows
  } catch (error) { message.error((error as Error).message) }
}

function exportCsv() {
  const header = ['商品编码','商品名称','销售数量','销售金额','退货金额','净销售','净成本','毛利','毛利率%','政策返利成本','政策后净利','净利率%']
  const lines = rows.value.map((r) => [r.productNo, r.productName, r.salesQuantity, r.salesAmount,
    r.salesReturnAmount, r.netSalesAmount, r.netCostAmount, r.grossProfit, r.grossMargin, r.rebateCost,
    r.netProfit, r.netMargin])
  const csv = [header, ...lines].map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '毛利分析.csv'
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(reload)
</script>
