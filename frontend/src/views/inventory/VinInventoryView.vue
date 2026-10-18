<template>
  <div>
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>VIN / SN 库存</span>
          <a-tag color="blue">采购收货自动建档</a-tag>
          <a-tag color="green">单车一价</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-button @click="reload">刷新</a-button>
          <a-button type="primary" @click="router.push('/procurement/orders')">去采购入库</a-button>
        </a-space>
      </template>
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="本页是采购收货后自动生成的一物一档库存明细，每台车/每块电池一条记录。VIN/SN 不预先在主数据中新建，采购收货时录入 VIN/SN 与单车采购价，系统自动建档并入库。" />
      <a-space wrap style="margin-bottom:12px">
        <a-input v-model:value="keyword" placeholder="VIN / SN / 商品" allow-clear style="width:240px"
                 @press-enter="reload" />
        <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选"
                  style="width:190px" @change="reload" />
        <a-select v-model:value="status" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:160px" @change="reload" />
        <a-button @click="reload">查询</a-button>
        <span class="hint">共 {{ rows.length }} 条，采购金额合计 {{ money(totalPurchaseAmount) }}</span>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1350 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'purchasePrice'">
            {{ money(record.purchasePrice) }}
          </template>
          <template v-else-if="column.key === 'ownershipType'">
            {{ ownershipLabel(record.ownershipType) }}
          </template>
          <template v-else-if="column.key === 'inboundAt'">
            {{ dateTime(record.inboundAt ?? record.createdAt) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { serialApi, warehouseApi } from '../../api/masterdata'
import type { ProductSerial, SerialStatus } from '../../types/masterdata'
import { SERIAL_STATUS_LABELS } from '../../types/masterdata'

const router = useRouter()
const rows = ref<ProductSerial[]>([])
const keyword = ref('')
const status = ref<string | undefined>(undefined)
const warehouseId = ref<string | undefined>(undefined)
const warehouseOptions = ref<{ label: string; value: string }[]>([])

const columns = [
  { title: 'VIN / SN', key: 'serialNo', dataIndex: 'serialNo', width: 210 },
  { title: '商品编码', key: 'productNo', dataIndex: 'productNo', width: 130 },
  { title: '商品名称', key: 'productName', dataIndex: 'productName', width: 210 },
  { title: '状态', key: 'status', width: 100 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 140 },
  { title: '采购单价', key: 'purchasePrice', width: 130 },
  { title: '库位', key: 'locationCode', dataIndex: 'locationCode', width: 110 },
  { title: '所有权', key: 'ownershipType', width: 100 },
  { title: '入库时间', key: 'inboundAt', width: 170 },
  { title: '备注', key: 'remark', dataIndex: 'remark', ellipsis: true },
]

const statusOptions = Object.entries(SERIAL_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const totalPurchaseAmount = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.purchasePrice ?? 0), 0),
)

function statusLabel(value: SerialStatus | string) {
  return SERIAL_STATUS_LABELS[value as SerialStatus] ?? value
}

function statusColor(value: SerialStatus) {
  if (value === 'IN_STOCK') return 'green'
  if (value === 'LOCKED') return 'orange'
  if (value === 'SOLD' || value === 'DELIVERED') return 'blue'
  if (value === 'SCRAPPED') return 'red'
  return 'default'
}

function ownershipLabel(value: string) {
  if (value === 'OWNED') return '自有'
  if (value === 'LEASE_TO_OWN') return '以租代售'
  if (value === 'LEASED') return '租赁'
  return value
}

function money(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function dateTime(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function reload() {
  try {
    rows.value = await serialApi.list({
      keyword: keyword.value || undefined,
      status: status.value,
      warehouseId: warehouseId.value,
    })
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
