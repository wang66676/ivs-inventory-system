<template>
  <div>
    <a-card :bordered="false">
      <a-space wrap style="margin-bottom:16px">
        <a-range-picker v-model:value="range" :allow-clear="false" @change="reload" />
        <a-button @click="reload">查询</a-button>
        <a-button type="primary" @click="exportCsv">导出 CSV</a-button>
      </a-space>

      <a-row :gutter="16" style="margin-bottom:16px">
        <a-col :span="6"><a-card size="small"><a-statistic title="净采购" :value="totals.netPurchase" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="6"><a-card size="small"><a-statistic title="净销售" :value="totals.netSales" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="6"><a-card size="small"><a-statistic title="毛利" :value="totals.grossProfit" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :span="6"><a-card size="small"><a-statistic title="期末库存资金" :value="totals.stockValue" :precision="2" prefix="¥" /></a-card></a-col>
      </a-row>

      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="月报汇总采购收货、采购退货、销售出库、销售退货、库存成本、政策返利与调拨影响；期末库存资金按库存台账金额重建。" />
      <a-table :data-source="rows" :columns="columns" row-key="period" size="middle"
               :pagination="false" :scroll="{ x: 1750 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'purchase'">{{ money(record.purchaseAmount) }}</template>
          <template v-else-if="column.key === 'purchaseReturn'">{{ money(record.purchaseReturnAmount) }}</template>
          <template v-else-if="column.key === 'netPurchase'">{{ money(record.netPurchaseAmount) }}</template>
          <template v-else-if="column.key === 'sales'">{{ money(record.salesAmount) }}</template>
          <template v-else-if="column.key === 'salesReturn'">{{ money(record.salesReturnAmount) }}</template>
          <template v-else-if="column.key === 'netSales'">{{ money(record.netSalesAmount) }}</template>
          <template v-else-if="column.key === 'cost'">{{ money(record.costAmount) }}</template>
          <template v-else-if="column.key === 'gross'">
            <span :class="Number(record.grossProfit) >= 0 ? 'positive' : 'negative'">{{ money(record.grossProfit) }}</span>
          </template>
          <template v-else-if="column.key === 'rebate'">{{ money(record.rebateCost) }}</template>
          <template v-else-if="column.key === 'transfer'">
            {{ money(record.transferOutAmount) }} / {{ money(record.transferInAmount) }}
          </template>
          <template v-else-if="column.key === 'stock'">{{ money(record.stockValue) }}</template>
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
import type { MonthlyReportRow } from '../../types/report'

const rows = ref<MonthlyReportRow[]>([])
const range = ref<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()])

const columns = [
  { title: '期间', key: 'period', dataIndex: 'period', width: 100, fixed: 'left' },
  { title: '采购入库', key: 'purchase', width: 130 },
  { title: '采购退货', key: 'purchaseReturn', width: 130 },
  { title: '净采购', key: 'netPurchase', width: 130 },
  { title: '销售出库', key: 'sales', width: 130 },
  { title: '销售退货', key: 'salesReturn', width: 130 },
  { title: '净销售', key: 'netSales', width: 130 },
  { title: '销售成本', key: 'cost', width: 130 },
  { title: '毛利', key: 'gross', width: 130 },
  { title: '政策返利成本', key: 'rebate', width: 150 },
  { title: '调拨出/入', key: 'transfer', width: 180 },
  { title: '期末库存资金', key: 'stock', width: 150 },
]

const totals = computed(() => rows.value.reduce((acc, row) => ({
  netPurchase: acc.netPurchase + Number(row.netPurchaseAmount),
  netSales: acc.netSales + Number(row.netSalesAmount),
  grossProfit: acc.grossProfit + Number(row.grossProfit),
  stockValue: Number(row.stockValue),
}), { netPurchase: 0, netSales: 0, grossProfit: 0, stockValue: 0 }))

function money(value?: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }

async function reload() {
  try {
    const report = await reportApi.monthly({
      from: range.value[0].format('YYYY-MM-DD'), to: range.value[1].format('YYYY-MM-DD'),
    })
    rows.value = report.rows
  } catch (error) { message.error((error as Error).message) }
}

function exportCsv() {
  const header = ['期间','采购入库','采购退货','净采购','销售出库','销售退货','净销售','销售成本','毛利','政策返利成本','调拨出','调拨入','期末库存资金']
  const lines = rows.value.map((r) => [r.period, r.purchaseAmount, r.purchaseReturnAmount, r.netPurchaseAmount,
    r.salesAmount, r.salesReturnAmount, r.netSalesAmount, r.costAmount, r.grossProfit, r.rebateCost,
    r.transferOutAmount, r.transferInAmount, r.stockValue])
  downloadCsv('进销存月报.csv', [header, ...lines])
}

function downloadCsv(name: string, data: unknown[][]) {
  const csv = data.map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = name
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(reload)
</script>

<style scoped>
.positive { color: #389e0d; font-weight: 600; }
.negative { color: #cf1322; font-weight: 600; }
</style>
