<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:14px"
               message="统计看板与采购收货、销售出库实时联动。采购数量按实际收货入库统计，销售数量按实际出库统计；已取消、已退回和未过账数据不计入。" />
      <a-space wrap style="margin-bottom:14px">
        <a-range-picker v-model:value="dateRange" value-format="YYYY-MM-DD" format="YYYY-MM-DD" allow-clear style="width:260px" />
        <a-segmented v-model:value="granularity" :options="granularityOptions" />
        <a-select v-model:value="businessScope" :options="scopeOptions" style="width:150px" />
        <a-select v-model:value="warehouseType" :options="warehouseTypeOptions" allow-clear placeholder="按仓库类型筛选" style="width:170px" />
        <a-button type="primary" :loading="loading" @click="load">查询</a-button>
        <a-button @click="exportStatistics">导出 CSV</a-button>
      </a-space>

      <a-row :gutter="16">
        <a-col :xs="24" :md="12" :xl="6">
          <a-card :bordered="false" class="metric-card">
            <a-statistic title="采购数量" :value="summary?.totalPurchaseQuantity ?? 0" suffix="件/台" />
            <div class="metric-note">采购订单数：{{ summary?.purchaseOrderCount ?? 0 }}</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="12" :xl="6">
          <a-card :bordered="false" class="metric-card">
            <a-statistic title="销售数量" :value="summary?.totalSalesQuantity ?? 0" suffix="件/台" />
            <div class="metric-note">销售订单数：{{ summary?.salesOrderCount ?? 0 }}</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="12" :xl="6">
          <a-card :bordered="false" class="metric-card">
            <a-statistic title="购销差额" :value="summary?.netQuantity ?? 0" suffix="件/台" />
            <div class="metric-note">销售数量 - 采购数量</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="12" :xl="6">
          <a-card :bordered="false" class="metric-card">
            <a-statistic title="统计区间" :value="statistics ? `${statistics.from} 至 ${statistics.to}` : '-'" />
            <div class="metric-note">{{ granularity === 'DAY' ? '按日统计' : '按月统计' }}</div>
          </a-card>
        </a-col>
      </a-row>
    </a-card>

    <a-row :gutter="16" style="margin-top:16px">
      <a-col :xs="24" :xl="16">
        <a-card title="采购与销售趋势" :bordered="false">
          <a-table :data-source="trendRows" row-key="period" size="small"
                   :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 720 }">
            <a-table-column title="期间" data-index="period" :width="130" />
            <a-table-column title="采购数量" :width="150" align="right">
              <template #default="{ record }">{{ quantity(record.purchaseQuantity) }}</template>
            </a-table-column>
            <a-table-column title="销售数量" :width="150" align="right">
              <template #default="{ record }">{{ quantity(record.salesQuantity) }}</template>
            </a-table-column>
            <a-table-column title="趋势对比" :width="290">
              <template #default="{ record }">
                <div class="bar-row"><span class="bar-label">采</span><span class="bar purchase" :style="{ width: barWidth(record.purchaseQuantity) }" /></div>
                <div class="bar-row"><span class="bar-label">销</span><span class="bar sales" :style="{ width: barWidth(record.salesQuantity) }" /></div>
              </template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="8">
        <a-card title="按仓库类型统计" :bordered="false">
          <a-table :data-source="warehouseRows" row-key="warehouseType" size="small" :pagination="false">
            <a-table-column title="仓库类型" data-index="warehouseTypeLabel" :width="120" />
            <a-table-column title="采购数量" :width="90" align="right">
              <template #default="{ record }">{{ quantity(record.purchaseQuantity) }}</template>
            </a-table-column>
            <a-table-column title="销售数量" :width="90" align="right">
              <template #default="{ record }">{{ quantity(record.salesQuantity) }}</template>
            </a-table-column>
          </a-table>
        </a-card>
        <a-card title="统计口径" :bordered="false" style="margin-top:16px">
          <ul class="notes"><li v-for="note in statistics?.notes ?? []" :key="note">{{ note }}</li></ul>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'
import type { InventoryStatistics } from '../../types/inventory'

const loading = ref(false)
const statistics = ref<InventoryStatistics>()
const dateRange = ref<[string, string]>([
  dayjs().startOf('month').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
])
const granularity = ref<'DAY' | 'MONTH'>('DAY')
const businessScope = ref<'ALL' | 'VEHICLE' | 'BATTERY'>('ALL')
const warehouseType = ref<string>()
const granularityOptions = [{ label: '按日', value: 'DAY' }, { label: '按月', value: 'MONTH' }]
const scopeOptions = [
  { label: '全部业务', value: 'ALL' }, { label: '整车', value: 'VEHICLE' }, { label: '动力电池', value: 'BATTERY' },
]
const warehouseTypeOptions = [
  { label: '主仓', value: 'MAIN' }, { label: '门店仓', value: 'STORE' }, { label: '二级仓', value: 'SECONDARY' },
  { label: '客户仓', value: 'CUSTOMER' }, { label: '临时仓', value: 'TEMPORARY' }, { label: '其他', value: 'OTHER' },
]
const summary = computed(() => statistics.value?.summary)
const trendRows = computed(() => [...(statistics.value?.trend ?? [])].reverse())
const warehouseRows = computed(() => (statistics.value?.byWarehouseType ?? [])
  .filter((row) => Number(row.purchaseQuantity) !== 0 || Number(row.salesQuantity) !== 0))
const maxQuantity = computed(() => Math.max(1, ...(statistics.value?.trend ?? []).flatMap((row) =>
  [Number(row.purchaseQuantity), Number(row.salesQuantity)])))

function quantity(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}
function barWidth(value: number) {
  return `${Math.max(0, Math.min(100, Number(value || 0) / maxQuantity.value * 100))}%`
}
async function load() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) { message.warning('请选择统计日期范围'); return }
  loading.value = true
  try {
    statistics.value = await inventoryApi.statistics({
      from: dateRange.value[0], to: dateRange.value[1], granularity: granularity.value,
      businessScope: businessScope.value, warehouseType: warehouseType.value,
    })
  } catch (error) { message.error((error as Error).message) }
  finally { loading.value = false }
}
function exportStatistics() {
  if (!statistics.value) { message.warning('请先查询统计数据'); return }
  exportCsv(`库存统计-${exportTimestamp()}`, [
    { title: '期间', value: (row) => row.period },
    { title: '采购数量', value: (row) => row.purchaseQuantity },
    { title: '销售数量', value: (row) => row.salesQuantity },
  ], statistics.value.trend)
}
onMounted(load)
</script>

<style scoped>
.metric-card { height: 100%; }
.metric-note { margin-top: 8px; color: #8b96a8; font-size: 12px; }
.bar-row { display: flex; align-items: center; gap: 7px; margin: 3px 0; }
.bar-label { width: 16px; color: #8b96a8; font-size: 11px; }
.bar { display: inline-block; height: 8px; border-radius: 99px; transition: width .2s ease; }
.purchase { background: #1677ff; }
.sales { background: #52c41a; }
.notes { margin: 0; padding-left: 18px; color: #6b7280; line-height: 1.8; }
</style>
