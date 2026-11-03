<template>
  <div>
    <a-row :gutter="16">
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false"><a-statistic title="在库整车（未锁定）" :value="summary?.vehicleInStockNotLocked ?? 0" suffix="台" /></a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false"><a-statistic title="在库电池" :value="summary?.batteryInStock ?? 0" suffix="块" /></a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false"><a-statistic title="数量商品条目" :value="summary?.quantityItems ?? 0" /></a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false">
          <a-statistic title="库存资金占用" :value="summary?.totalAmount ?? 0" :precision="2" prefix="¥" />
        </a-card>
      </a-col>
    </a-row>

    <a-alert v-if="summary" :type="summary.consistent ? 'success' : 'error'" show-icon style="margin-top:16px"
             :message="summary.consistent ? `台账与余额一致，共 ${summary.ledgerCount} 条台账` : '台账与余额不一致，请立即排查'" />

    <a-row :gutter="16" style="margin-top:16px">
      <a-col :span="12">
        <a-card title="车系 VIN 库存" :bordered="false">
          <div class="hint" style="margin-bottom:8px">未销售数量只统计在库未锁定车辆；已锁定 VIN 视为已销售，仅单独备注。</div>
          <a-table :data-source="summary?.vehicleSeriesInventory ?? []" row-key="series" size="small" :pagination="false">
            <a-table-column title="车系" data-index="series" :width="180" />
            <a-table-column title="未销售 VIN 数" data-index="unsoldVinCount" :width="130" align="right" />
            <a-table-column title="已锁定 VIN 数（视为已销售）" data-index="lockedVinCount" :width="200" align="right" />
          </a-table>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="按产品大项" :bordered="false">
          <a-table :data-source="summary?.byCategory ?? []" row-key="categoryName" size="small" :pagination="false">
            <a-table-column title="大项" data-index="categoryName" :width="140" />
            <a-table-column title="条目数" data-index="itemCount" :width="100" />
            <a-table-column title="金额" data-index="amount" />
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="按仓库" :bordered="false" style="margin-top:16px">
      <a-table :data-source="summary?.byWarehouse ?? []" row-key="warehouseName" size="small" :pagination="false">
        <a-table-column title="仓库" data-index="warehouseName" :width="200" />
        <a-table-column title="条目数" data-index="itemCount" :width="120" />
        <a-table-column title="金额" data-index="amount" />
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import type { InventorySummary } from '../../types/inventory'

const summary = ref<InventorySummary | null>(null)

onMounted(async () => {
  try {
    summary.value = await inventoryApi.summary()
  } catch (error) {
    message.error((error as Error).message)
  }
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; margin-top: 4px; }
</style>
