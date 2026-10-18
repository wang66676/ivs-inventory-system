<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库存余额 = 台账的可重建投影：在库 / 锁定 / 可用数量与成本。数量商品按移动加权平均，一物一档商品（VIN/SN）按单台个别计价。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选"
                  style="width:200px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="商品 / 大项 / 仓库" allow-clear style="width:240px"
                 @press-enter="reload" />
        <a-button @click="reload">查询</a-button>
        <span class="hint">共 {{ rows.length }} 条</span>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1100 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'trackingMode'">
            <a-tag :color="record.trackingMode === 'SERIAL' ? 'blue' : 'default'">
              {{ record.trackingMode === 'SERIAL' ? 'VIN / SN' : '数量' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ String(record.updatedAt).replace('T', ' ').slice(0, 19) }}
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
import { warehouseApi } from '../../api/masterdata'
import type { StockBalance } from '../../types/inventory'

const rows = ref<StockBalance[]>([])
const warehouseId = ref<string | undefined>(undefined)
const keyword = ref('')
const warehouseOptions = ref<{ label: string; value: string }[]>([])

const columns = [
  { title: '商品编码', key: 'productNo', dataIndex: 'productNo', width: 130 },
  { title: '商品名称', key: 'productName', dataIndex: 'productName', width: 220 },
  { title: '追踪', key: 'trackingMode', width: 100 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '批次', key: 'batchNo', dataIndex: 'batchNo', width: 110 },
  { title: '在库', key: 'onHandQty', dataIndex: 'onHandQty', width: 100 },
  { title: '锁定', key: 'lockedQty', dataIndex: 'lockedQty', width: 100 },
  { title: '可用', key: 'availableQty', dataIndex: 'availableQty', width: 100 },
  { title: '单位成本', key: 'unitCost', dataIndex: 'unitCost', width: 120 },
  { title: '金额', key: 'amount', dataIndex: 'amount', width: 140 },
  { title: '更新时间', key: 'updatedAt', width: 170 },
]

async function reload() {
  try {
    rows.value = await inventoryApi.balances({ warehouseId: warehouseId.value, keyword: keyword.value || undefined })
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  try {
    const warehouses = await warehouseApi.list()
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>
