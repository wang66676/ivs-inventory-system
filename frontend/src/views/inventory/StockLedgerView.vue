<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库存台账：只追加的事实表。每一笔变动都记录方向、数量、单位成本、金额与变动后余额，并关联业务单据（收货单号等）。台账可完整重建余额。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="productId" :options="productOptions" allow-clear show-search placeholder="按商品筛选"
                  style="width:280px" @change="reload" />
        <a-input-number v-model:value="limit" :min="10" :max="500" style="width:120px" @change="reload" />
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
          <template v-else-if="column.key === 'occurredAt'">
            {{ String(record.occurredAt).replace('T', ' ').slice(0, 19) }}
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
import { productApi } from '../../api/masterdata'
import { MOVE_TYPE_LABELS } from '../../types/inventory'
import type { LedgerEntry } from '../../types/inventory'

const rows = ref<LedgerEntry[]>([])
const productId = ref<string | undefined>(undefined)
const limit = ref(200)
const productOptions = ref<{ label: string; value: string }[]>([])

const columns = [
  { title: '发生时间', key: 'occurredAt', width: 170 },
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
    rows.value = await inventoryApi.ledger({ productId: productId.value, limit: limit.value })
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  try {
    const products = await productApi.list()
    productOptions.value = products.map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>