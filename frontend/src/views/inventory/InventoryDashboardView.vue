<template>
  <div>
    <a-row :gutter="16">
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false"><a-statistic title="在库整车" :value="summary?.vehicleInStockNotLocked ?? 0" suffix="台" /></a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false">
          <a-statistic title="整车占用资金" :value="summary?.vehicleInventoryAmount ?? 0" :precision="2" prefix="¥" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false"><a-statistic title="在库电池" :value="summary?.batteryInStock ?? 0" suffix="块" /></a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card :bordered="false">
          <a-statistic title="电池占用资金" :value="summary?.batteryInventoryAmount ?? 0" :precision="2" prefix="¥" />
        </a-card>
      </a-col>
    </a-row>

    <a-alert v-if="summary" :type="summary.consistent ? 'success' : 'error'" show-icon style="margin-top:16px"
             :message="summary.consistent ? `台账与余额一致，共 ${summary.ledgerCount} 条台账` : '台账与余额不一致，请立即排查'" />

    <a-row :gutter="16" style="margin-top:16px">
      <a-col :xs="24" :lg="18">
        <a-card title="车系 VIN 库存" :bordered="false">
          <div class="hint" style="margin-bottom:8px">数量仅统计状态为“在库”的 VIN/SN；已锁定车辆不计入。</div>
          <a-table :data-source="summary?.vehicleSeriesInventory ?? []" :row-key="(record: any) => `${record.series}-${record.config}`" size="small" :pagination="false" table-layout="fixed" :scroll="{ x: 520 }">
            <a-table-column title="车系" data-index="series" :width="150" ellipsis />
            <a-table-column title="配置" data-index="config" :width="290" ellipsis />
            <a-table-column title="数量" key="quantity" :width="80" align="right">
              <template #default="{ record }">
                <a-button type="link" size="small" style="padding:0" @click="openVinDetail(record)">
                  {{ record.quantity }}
                </a-button>
              </template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="6">
        <a-card title="按仓库" :bordered="false">
          <div class="hint" style="margin-bottom:8px">按未锁定整车在库数量汇总。</div>
          <a-table :data-source="summary?.byWarehouse ?? []" row-key="warehouseName" size="small" :pagination="false">
            <a-table-column title="仓库" data-index="warehouseName" :width="120" ellipsis />
            <a-table-column title="在库整车" data-index="itemCount" :width="90" align="right" />
            <a-table-column title="金额" :width="130" align="right">
              <template #default="{ record }">¥{{ money(record.amount) }}</template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="电池库存明细" :bordered="false" style="margin-top:16px">
      <div class="hint" style="margin-bottom:8px">按电池厂家与框架厂家汇总在库电池数量；已锁定电池不计入。</div>
      <a-table :data-source="batteryRows" row-key="batteryKey" size="small"
               :pagination="{ pageSize: 20, showSizeChanger: true }">
        <a-table-column title="电池厂家" data-index="batteryManufacturer" :width="260" />
        <a-table-column title="框架厂家" data-index="frameManufacturer" :width="260" />
        <a-table-column title="数量" data-index="quantity" :width="120" align="right" />
      </a-table>
    </a-card>

    <a-modal v-model:open="detailOpen" :title="detailTitle" :footer="null" width="1080px">
      <a-table :data-source="detailRows" row-key="serialId" size="small" :loading="detailLoading"
               :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 900 }">
        <a-table-column title="VIN / SN" data-index="serialNo" :width="230" />
        <a-table-column title="车系" data-index="series" :width="150" />
        <a-table-column title="采购配置" data-index="config" :width="200" />
        <a-table-column title="状态" :width="100">
          <template #default="{ record }">
            <a-tag color="green">{{ record.status === 'IN_STOCK' ? '在库' : record.status }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="当前仓库" data-index="warehouseName" :width="150" />
        <a-table-column title="对应采购订单号" data-index="purchaseOrderNo" :width="190" />
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import type { InventorySummary, VehicleSeriesInventoryDetail } from '../../types/inventory'

const summary = ref<InventorySummary | null>(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailRows = ref<VehicleSeriesInventoryDetail[]>([])
const detailTitle = ref('VIN / SN 明细')

const batteryRows = computed(() => (summary.value?.batteryInventory ?? []).map((record) =>
  ({ ...record, batteryKey: `${record.batteryManufacturer}-${record.frameManufacturer}` })))
function money(value?: number) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

async function openVinDetail(record: InventorySummary['vehicleSeriesInventory'][number]) {
  detailOpen.value = true
  detailLoading.value = true
  detailTitle.value = `${record.series} / ${record.config} VIN 明细`
  detailRows.value = []
  try {
    detailRows.value = await inventoryApi.vehicleSeriesDetail({ series: record.series, config: record.config })
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    detailLoading.value = false
  }
}

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
