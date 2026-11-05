<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库存锁定（预留）：销售订单锁货使用。锁定占用可用库存，超时（默认 7 天）由定时任务自动释放；也可在此手工释放或触发超时回收。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="status" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:160px" @change="reload" />
        <a-button v-if="canWrite" @click="releaseExpired">回收超时锁定</a-button>
        <span class="hint">共 {{ rows.length }} 条</span>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1100 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'orange' : 'default'">
              {{ lockStatusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'expiresAt'">
            {{ String(record.expiresAt).replace('T', ' ').slice(0, 19) }}
          </template>
          <template v-else-if="column.key === 'serialNo'">
            {{ record.serialNo ?? '-' }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a v-if="canWrite && record.status === 'ACTIVE'" @click="release(record)">释放</a>
            <span v-else class="hint">—</span>
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
import { useAuthStore } from '../../stores/auth'
import { LOCK_STATUS_LABELS } from '../../types/inventory'
import type { StockLock, StockLockStatus } from '../../types/inventory'
import type { BusinessScope } from '../../utils/businessScope'
import { isBatteryCategory } from '../../utils/businessScope'

const props = withDefaults(defineProps<{ scope?: BusinessScope }>(), { scope: 'VEHICLE' })
const lockStatusLabel = (status: string) => LOCK_STATUS_LABELS[status as StockLockStatus]

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')

const rows = ref<StockLock[]>([])
const status = ref<string | undefined>(undefined)
const scopedProductIds = ref<Set<string>>(new Set())

const columns = [
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 200 },
  { title: '序列号', key: 'serialNo', width: 190 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '锁定数量', key: 'quantity', dataIndex: 'quantity', width: 110 },
  { title: '业务类型', key: 'bizType', dataIndex: 'bizType', width: 140 },
  { title: '业务单据', key: 'bizNo', dataIndex: 'bizNo', width: 140 },
  { title: '到期时间', key: 'expiresAt', width: 170 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 100 },
]

const statusOptions = Object.entries(LOCK_STATUS_LABELS).map(([value, label]) => ({ value, label }))

async function reload() {
  try {
    const result = await inventoryApi.locks({ status: status.value })
    rows.value = result.filter((row) => scopedProductIds.value.has(row.productId))
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function release(record: StockLock) {
  try {
    await inventoryApi.releaseLock(record.id)
    message.success('已释放')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function releaseExpired() {
  try {
    const released = await inventoryApi.releaseExpiredLocks()
    message.success(`已回收超时锁定 ${released} 条`)
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  try {
    const products = await productApi.list()
    scopedProductIds.value = new Set(products
      .filter((item) => isBatteryCategory(item.categoryCode, item.categoryName) === (props.scope === 'BATTERY'))
      .map((item) => item.id))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
</style>
