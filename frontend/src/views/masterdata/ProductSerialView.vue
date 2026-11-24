<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="系统中的 VIN/SN 统一由采购收货入库生成，不在商品档案中手工新建或批量导入。本页面仅用于查询收货形成的序列号档案及当前状态。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:160px" @change="reload" />
        <a-select v-model:value="filterWarehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选"
                  style="width:180px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="VIN / 电池 SN" allow-clear style="width:220px"
                 @press-enter="reload" />
        <a-button type="primary" @click="reload">查询</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 20, showSizeChanger: true }" :scroll="{ x: 1450 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'purchasePrice'">{{ money(record.purchasePrice) }}</template>
          <template v-else-if="column.key === 'inboundAt'">{{ formatDate(record.inboundAt) }}</template>
          <template v-else-if="column.key === 'ownershipType'">
            {{ record.ownershipType === 'OWNED' ? '自有' : record.ownershipType === 'LEASE_TO_OWN' ? '以租代售' : '租赁' }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { serialApi, warehouseApi } from '../../api/masterdata'
import { SERIAL_STATUS_LABELS } from '../../types/masterdata'
import type { ProductSerial, SerialStatus } from '../../types/masterdata'

const rows = ref<ProductSerial[]>([])
const keyword = ref('')
const filterStatus = ref<SerialStatus | undefined>()
const filterWarehouseId = ref<string | undefined>()
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const statusOptions = Object.entries(SERIAL_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const columns = [
  { title: '商品名称', key: 'productName', dataIndex: 'productName', width: 190, ellipsis: true },
  { title: '商品编码', key: 'productNo', dataIndex: 'productNo', width: 150, ellipsis: true },
  { title: 'VIN / SN', key: 'serialNo', dataIndex: 'serialNo', width: 220, fixed: 'left' as const },
  { title: '状态', key: 'status', width: 100, align: 'center' as const },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 140, ellipsis: true },
  { title: '采购单价', key: 'purchasePrice', width: 120, align: 'right' as const },
  { title: '入库时间', key: 'inboundAt', width: 150 },
  { title: '申请合格证日期', key: 'certificateApplicationDate', dataIndex: 'certificateApplicationDate', width: 150 },
  { title: '所有权', key: 'ownershipType', width: 100 },
  { title: '备注', key: 'remark', dataIndex: 'remark', width: 220, ellipsis: true },
]

const statusLabel = (status: string) => SERIAL_STATUS_LABELS[status as SerialStatus]
function statusColor(status: SerialStatus) {
  if (status === 'IN_STOCK') return 'green'
  if (status === 'SCRAPPED') return 'red'
  if (status === 'SOLD' || status === 'DELIVERED') return 'blue'
  if (status === 'LOCKED') return 'orange'
  return 'default'
}
function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function formatDate(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }

async function reload() {
  try {
    rows.value = await serialApi.list({
      keyword: keyword.value || undefined,
      status: filterStatus.value,
      warehouseId: filterWarehouseId.value,
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
:deep(.ant-table-thead > tr > th) { text-align: center !important; }
</style>
