<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="库存余额 = 台账的可重建投影：在库 / 锁定 / 可用数量与成本。数量商品按移动加权平均，一物一档商品（VIN/SN）按单台个别计价。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear placeholder="按仓库筛选"
                  style="width:200px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="订单编号 / 商品 / 仓库" allow-clear style="width:260px"
                 @press-enter="reload" />
        <a-button @click="reload">查询</a-button>
        <a-button @click="exportStock">导出</a-button>
        <span class="hint">共 {{ rows.length }} 条</span>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1250 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'purchaseOrderNo'">
            <a v-if="record.purchaseOrderId" @click="openOrderDetail(record)">{{ record.purchaseOrderNo || '-' }}</a>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ String(record.updatedAt).replace('T', ' ').slice(0, 19) }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="orderDetailOpen" title="采购订单详情" footer="" width="1100px">
      <a-spin :spinning="orderLoading">
        <template v-if="orderDetail">
          <a-descriptions bordered size="small" :column="3" style="margin-bottom:12px">
            <a-descriptions-item label="订单编号">{{ orderDetail.externalOrderNo || orderDetail.orderNo }}</a-descriptions-item>
            <a-descriptions-item label="供应商">{{ orderDetail.supplierName }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ PURCHASE_STATUS_LABELS[orderDetail.status] }}</a-descriptions-item>
            <a-descriptions-item label="预计到货">{{ orderDetail.expectedDate || '-' }}</a-descriptions-item>
            <a-descriptions-item label="总金额">{{ money(orderDetail.totalAmount) }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ orderDetail.remark || '-' }}</a-descriptions-item>
          </a-descriptions>
          <a-table :data-source="orderDetail.lines" row-key="id" size="small" :pagination="false" :scroll="{ x: 1100 }">
            <a-table-column title="车系" :width="140"><template #default="{ record }">{{ record.series || '-' }}</template></a-table-column>
            <a-table-column title="商品名称" :width="190"><template #default="{ record }">{{ record.productName }}</template></a-table-column>
            <a-table-column title="配置" :width="160"><template #default="{ record }">{{ record.configText || record.configCode || '-' }}</template></a-table-column>
            <a-table-column title="数量" data-index="quantity" :width="80" />
            <a-table-column title="单价" :width="110"><template #default="{ record }">{{ money(record.unitPrice) }}</template></a-table-column>
            <a-table-column title="总计车款" :width="120"><template #default="{ record }">{{ money(record.amount) }}</template></a-table-column>
            <a-table-column title="返利政策" :width="220"><template #default="{ record }"><a-tag v-for="policy in record.policies" :key="policy.id" color="blue">{{ policy.name }}</a-tag></template></a-table-column>
            <a-table-column title="收货仓库" :width="140"><template #default="{ record }">{{ record.warehouseName || orderDetail?.warehouseName || '-' }}</template></a-table-column>
          </a-table>
        </template>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import { purchaseApi } from '../../api/procurement'
import { warehouseApi } from '../../api/masterdata'
import type { StockBalance } from '../../types/inventory'
import type { PurchaseOrder } from '../../types/procurement'
import { PURCHASE_STATUS_LABELS } from '../../types/procurement'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'

const rows = ref<StockBalance[]>([])
const warehouseId = ref<string | undefined>(undefined)
const keyword = ref('')
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const orderDetailOpen = ref(false)
const orderLoading = ref(false)
const orderDetail = ref<PurchaseOrder>()

const columns = [
  { title: '订单编号', key: 'purchaseOrderNo', width: 150, ellipsis: true },
  { title: '商品名称', key: 'productName', dataIndex: 'productName', width: 220 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 150 },
  { title: '在库', key: 'onHandQty', dataIndex: 'onHandQty', width: 100 },
  { title: '锁定', key: 'lockedQty', dataIndex: 'lockedQty', width: 100 },
  { title: '可用', key: 'availableQty', dataIndex: 'availableQty', width: 100 },
  { title: '单位成本', key: 'unitCost', dataIndex: 'unitCost', width: 110 },
  { title: '金额', key: 'amount', dataIndex: 'amount', width: 140 },
  { title: '更新时间', key: 'updatedAt', width: 170 },
]

function money(value?: number | null) {
  return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function exportStock() {
  if (!rows.value.length) { message.warning('当前没有可导出的库存数据'); return }
  exportCsv<StockBalance>(`库存查询-${exportTimestamp()}`, [
    { title: '订单编号', value: (row) => row.purchaseOrderNo },
    { title: '商品名称', value: (row) => row.productName },
    { title: '仓库', value: (row) => row.warehouseName },
    { title: '在库', value: (row) => row.onHandQty },
    { title: '锁定', value: (row) => row.lockedQty },
    { title: '可用', value: (row) => row.availableQty },
    { title: '单位成本', value: (row) => row.unitCost },
    { title: '金额', value: (row) => row.amount },
    { title: '更新时间', value: (row) => String(row.updatedAt).replace('T', ' ').slice(0, 19) },
  ], rows.value)
  message.success('库存数据已导出')
}

async function openOrderDetail(record: StockBalance) {
  if (!record.purchaseOrderId) return
  orderDetailOpen.value = true
  orderLoading.value = true
  orderDetail.value = undefined
  try { orderDetail.value = await purchaseApi.get(record.purchaseOrderId) }
  catch (error) { message.error((error as Error).message); orderDetailOpen.value = false }
  finally { orderLoading.value = false }
}

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
